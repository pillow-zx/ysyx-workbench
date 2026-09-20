#include <cstdint>
#include <iostream>
#include <optional>
#include <print>
#include <string>
#include <string_view>
#include <vector>

#include <mm.hh>
#include <monitor.hh>
#include <npc.hh>

Npc::Npc(const int argc, char *argv[], const NpcOptions &options)
	: cpu_(argc, argv, options.log, options.elf), batchMode_(options.batch) {
	if (!Memory::init(options.image)) {
		status_ = NpcStatus::Exit;
		return;
	}

	cpu_.reset();
}

Npc::~Npc() {
	if (status_ == NpcStatus::Exit) {
		std::cout << "HIT GOOD TRAP" << std::endl;
	} else {
		std::cout << "HIT BAD TRAP" << std::endl;
	}
}

auto Npc::executeCpu() -> void {
	if (cpu_.exec()) {
		status_ = NpcStatus::Exit;
	}
}

auto Npc::executeCpu(const std::size_t cycles) -> void {
	if (cpu_.exec(cycles)) {
		status_ = NpcStatus::Exit;
	}
}

auto Npc::showRegisters() const -> void {
	const auto flags = std::cout.flags();
	const auto fill = std::cout.fill();

	for (std::size_t index = 0; index < registerNames.size(); ++index) {
		std::print("{:<4} 0x{:08x}{}", registerNames[index], cpu_.getReg(index), (index % 4 == 3) ? '\n' : ' ');
	}

	std::cout.flags(flags);
	std::cout.fill(fill);
}

auto Npc::run() -> void {
	if (status_ == NpcStatus::Exit) {
		return;
	}

	if (batchMode_) {
		std::cout << "Running in batch mode..." << std::endl;
		executeCpu();
		return;
	}

	const monitor::ExprContext expressionContext{
			.readRegister = [this](const std::string_view name) -> std::optional<std::uint32_t> {
				if (name == "pc") {
					return cpu_.getPc();
				}
				if (name == "0" || name == "%0") {
					return cpu_.getReg(0);
				}
				for (std::size_t index = 1; index < registerNames.size(); ++index) {
					if (registerNames[index] == name) {
						return cpu_.getReg(index);
					}
				}
				return std::nullopt;
			},
			.readMemory =
					[](const std::uint32_t address, const std::size_t size) {
						return Memory::readData(address, static_cast<std::uint32_t>(size));
					},
	};

	while (status_ != NpcStatus::Exit) {
		const auto commands = getCommands("(npc) > ");
		if (commands.empty()) {
			continue;
		}

		try {
			if (commands[0] == "q") {
				status_ = NpcStatus::Exit;
			} else if (commands[0] == "c") {
				executeCpu();
			} else if (commands[0] == "si") {
				const auto cycles = commands.size() > 1 ? std::stoull(commands[1]) : 1;
				executeCpu(cycles);
			} else if (commands[0] == "info" && commands.size() > 1 && commands[1] == "r") {
				showRegisters();
			} else if (commands[0] == "p" && commands.size() > 1) {
				const auto expr = joinArguments(commands, 1);
				const auto result = monitor::expr(expr, expressionContext);
				if (!result) {
					const auto error = result.error();
					std::println("Expression error at column {}: {}", error.position + 1, error.message);
				} else {
					std::println("0x{:08x} ({})", result.value(), result.value());
				}
			} else if (commands[0] == "x" && commands.size() > 2) {
				const auto len = std::stoull(commands[1]);
				const auto expression = joinArguments(commands, 2);
				const auto result = monitor::expr(expression, expressionContext);
				if (!result) {
					const auto error = result.error();
					std::println("Expression error at column {}: {}", error.position + 1, error.message);
				} else {
					Memory::showMemory(result.value(), static_cast<std::uint32_t>(len));
				}
			} else {
				std::cout << "Unknown command." << std::endl;
			}
		} catch (const std::exception &error) {
			status_ = NpcStatus::Exit;
			std::cout << "Invalid command argument: " << error.what() << std::endl;
		}
	}
}
