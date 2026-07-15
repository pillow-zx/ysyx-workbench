#ifndef NPC_MONITOR_HH
#define NPC_MONITOR_HH

#include <vector>
#include <string>
#include <string_view>

auto getCommands(std::string_view prompt) -> std::vector<std::string>;

#endif //NPC_SDB_HH