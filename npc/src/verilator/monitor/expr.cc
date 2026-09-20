#include <charconv>
#include <cstddef>
#include <string>
#include <string_view>
#include <utility>

#include <monitor.hh>

namespace monitor {
namespace {

constexpr auto isDigit(const char character) -> bool {
	return character >= '0' && character <= '9';
}

constexpr auto isHexDigit(const char character) -> bool {
	return isDigit(character) || (character >= 'a' && character <= 'f') || (character >= 'A' && character <= 'F');
}

constexpr auto isNameCharacter(const char character) -> bool {
	return isDigit(character) || (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') ||
		   character == '_' || character == '%';
}

constexpr auto isSpace(const char character) -> bool {
	return character == ' ' || character == '\t' || character == '\n' || character == '\r' || character == '\f' ||
		   character == '\v';
}

class Parser {
  public:
	Parser(const std::string_view source, const ExprContext &context) : source_(source), context_(context) {
	}

	[[nodiscard]] auto parse() -> std::uint32_t {
		const auto value = parseExpression();
		skipSpaces();
		if (!atEnd()) {
			fail("unexpected character '" + std::string(1, peek()) + "'");
		}
		return value;
	}

  private:
	[[noreturn]] auto fail(std::string message) const -> void {
		failAt(position_, std::move(message));
	}

	[[noreturn]] auto failAt(const std::size_t position, std::string message) const -> void {
		throw ExprError{.position = position, .message = std::move(message)};
	}

	[[nodiscard]] auto atEnd() const -> bool {
		return position_ >= source_.size();
	}

	[[nodiscard]] auto peek() const -> char {
		return atEnd() ? '\0' : source_[position_];
	}

	[[nodiscard]] auto startsWith(const std::string_view text) const -> bool {
		return source_.substr(position_).starts_with(text);
	}

	auto skipSpaces() -> void {
		while (!atEnd() && isSpace(peek())) {
			++position_;
		}
	}

	[[nodiscard]] auto consume(const char character) -> bool {
		skipSpaces();
		if (peek() != character) {
			return false;
		}
		++position_;
		return true;
	}

	[[nodiscard]] auto consume(const std::string_view text) -> bool {
		skipSpaces();
		if (!startsWith(text)) {
			return false;
		}
		position_ += text.size();
		return true;
	}

	[[nodiscard]] auto parseExpression() -> std::uint32_t {
		return parseLogicalAnd();
	}

	[[nodiscard]] auto parseLogicalAnd() -> std::uint32_t {
		auto lhs = parseEquality();
		while (consume("&&")) {
			const auto rhs = parseEquality();
			lhs = static_cast<std::uint32_t>((lhs != 0) && (rhs != 0));
		}
		return lhs;
	}

	[[nodiscard]] auto parseEquality() -> std::uint32_t {
		auto lhs = parseAdditive();

		while (true) {
			if (consume("==")) {
				lhs = static_cast<std::uint32_t>(lhs == parseAdditive());
			} else if (consume("!=")) {
				lhs = static_cast<std::uint32_t>(lhs != parseAdditive());
			} else {
				return lhs;
			}
		}
	}

	[[nodiscard]] auto parseAdditive() -> std::uint32_t {
		auto lhs = parseMultiplicative();

		while (true) {
			if (consume('+')) {
				lhs += parseMultiplicative();
			} else if (consume('-')) {
				lhs -= parseMultiplicative();
			} else {
				return lhs;
			}
		}
	}

	[[nodiscard]] auto parseMultiplicative() -> std::uint32_t {
		auto lhs = parseUnary();

		while (true) {
			skipSpaces();
			const auto operatorPosition = position_;

			if (consume('*')) {
				lhs *= parseUnary();
			} else if (consume('/')) {
				const auto rhs = parseUnary();
				if (rhs == 0) {
					failAt(operatorPosition, "division by zero");
				}
				lhs /= rhs;
			} else {
				return lhs;
			}
		}
	}

	[[nodiscard]] auto parseUnary() -> std::uint32_t {
		if (!consume('*')) {
			return parsePrimary();
		}

		if (!context_.readMemory) {
			fail("memory reader is not configured");
		}

		const auto address = parseUnary();
		return context_.readMemory(address, sizeof(std::uint32_t));
	}

	[[nodiscard]] auto parsePrimary() -> std::uint32_t {
		skipSpaces();
		if (atEnd()) {
			fail("expected an expression");
		}

		if (isDigit(peek())) {
			return parseNumber();
		}
		if (peek() == '$') {
			return parseRegister();
		}
		if (consume('(')) {
			const auto result = parseExpression();
			if (!consume(')')) {
				fail("expected ')'");
			}
			return result;
		}

		fail("unexpected character '" + std::string(1, peek()) + "'");
	}

	[[nodiscard]] auto parseNumber() -> std::uint32_t {
		const auto begin = position_;
		const bool hexadecimal = peek() == '0' && position_ + 1 < source_.size() &&
								 (source_[position_ + 1] == 'x' || source_[position_ + 1] == 'X');

		if (hexadecimal) {
			position_ += 2;
		}

		const auto digitsBegin = position_;
		while (!atEnd() && (hexadecimal ? isHexDigit(peek()) : isDigit(peek()))) {
			++position_;
		}

		if (digitsBegin == position_) {
			failAt(begin, "hexadecimal literal requires at least one digit");
		}

		if (hexadecimal && !atEnd() && isNameCharacter(peek())) {
			failAt(position_, "invalid character in hexadecimal literal");
		}

		const auto digits = source_.substr(digitsBegin, position_ - digitsBegin);
		std::uint32_t value = 0;
		const auto [end, status] =
				std::from_chars(digits.data(), digits.data() + digits.size(), value, hexadecimal ? 16 : 10);

		if (status == std::errc::result_out_of_range) {
			failAt(begin, "integer literal does not fit in 32 bits");
		}
		if (status != std::errc{} || end != digits.data() + digits.size()) {
			failAt(begin, "invalid integer literal");
		}
		return value;
	}

	[[nodiscard]] auto parseRegister() -> std::uint32_t {
		const auto begin = position_;
		++position_;

		const auto nameBegin = position_;
		while (!atEnd() && isNameCharacter(peek())) {
			++position_;
		}

		if (nameBegin == position_) {
			failAt(begin, "expected a register name");
		}

		if (!context_.readRegister) {
			failAt(begin, "register reader is not configured");
		}

		const auto name = source_.substr(nameBegin, position_ - nameBegin);
		if (const auto value = context_.readRegister(name)) {
			return *value;
		}

		failAt(begin, "unknown register '" + std::string(source_.substr(begin, position_ - begin)) + "'");
	}

	std::string_view source_;
	const ExprContext &context_;
	std::size_t position_ = 0;
};

} // namespace

auto expr(const std::string_view expression, const ExprContext &context) -> std::expected<std::uint32_t, ExprError> {
	try {
		return Parser(expression, context).parse();
	} catch (const ExprError &error) {
		return std::unexpected(error);
	}
}

} // namespace monitor
