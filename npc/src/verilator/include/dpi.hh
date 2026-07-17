#ifndef NPC_DPI_HH
#define NPC_DPI_HH

class DpicBridge;

auto bindDpiAddressSpace(DpicBridge &bridge) -> void;

auto unbindDpiAddressSpace(const DpicBridge &bridge) -> void;

#endif // NPC_DPI_HH
