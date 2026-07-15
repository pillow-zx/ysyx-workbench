#include <iostream>
#include <vector>
#include <string>
#include <string_view>
#include <sstream>

#include <monitor.hh>

static auto getString(const std::string_view prompt = "(npc) >") -> std::string {
    std::cout << prompt;
    std::string command;
    std::getline(std::cin, command);
    return command;
}

static auto splitString(const std::string &str, char delimiter) -> std::vector<std::string> {
    std::vector<std::string> tokens;
    std::stringstream ss(str);
    std::string token;
    while (std::getline(ss, token, delimiter)) {
        if (!token.empty()) {
            tokens.push_back(token);
        }
    }
    return tokens;
}

auto getCommands(const std::string_view prompt = "(npc) >") -> std::vector<std::string>  {
    const auto command = getString(prompt);
    if (command.empty()) {
        return {};
    }

    auto tokens = splitString(command, ' ');
    return tokens;
}

