#include "VCore__Dpi.h"

#include <cassert>
#include <cstdint>

#include <dpicbridge.hh>
#include <dpi.hh>

namespace {
DpicBridge *dpiAddressSpace = nullptr;
}

auto bindDpiAddressSpace(DpicBridge &bridge) -> void {
    assert(dpiAddressSpace == nullptr);
    dpiAddressSpace = &bridge;
}

auto unbindDpiAddressSpace(const DpicBridge &bridge) -> void {
    assert(dpiAddressSpace == &bridge);
    dpiAddressSpace = nullptr;
}

extern "C" auto fetchInst(const int addr, int *inst) -> void {
    assert(dpiAddressSpace != nullptr);
    *inst = static_cast<int>(dpiAddressSpace->fetchInst(static_cast<std::uint32_t>(addr)));
}

extern "C" auto readData(const int addr, int *data) -> void {
    assert(dpiAddressSpace != nullptr);
    *data = static_cast<int>(dpiAddressSpace->readData(static_cast<std::uint32_t>(addr)));
}

extern "C" auto writeData(const int addr, const int data, const char wmask) -> void {
    assert(dpiAddressSpace != nullptr);
    dpiAddressSpace->writeData(static_cast<std::uint32_t>(addr), static_cast<std::uint32_t>(data),
                               static_cast<std::uint8_t>(wmask));
}
