#include <iostream>
#include <ranges>
#include <string>
#include <string_view>
#include <vector>

#include <monitor.hh>

static auto getString(const std::string_view prompt = "(npc) >") -> std::string {
    std::cout << prompt;
    std::string command;
    std::getline(std::cin, command);
    return command;
}

static auto splitString(const std::string_view text, const char delimiter) -> std::vector<std::string> {
    std::vector<std::string> tokens;
    for (auto &&token : text | std::views::split(delimiter)) {
        if (!token.empty()) {
            tokens.emplace_back(token.begin(), token.end());
        }
    }
    return tokens;
}

auto getCommands(const std::string_view prompt = "(npc) >") -> std::vector<std::string> {
    const auto command = getString(prompt);
    if (command.empty()) {
        return {};
    }

    auto tokens = splitString(command, ' ');
    return tokens;
}
