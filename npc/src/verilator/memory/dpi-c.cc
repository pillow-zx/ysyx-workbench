#include <cassert>
#include <cstdint>

#include <mm.hh>

extern "C" void flash_read(int32_t addr, int32_t *data) {
    (void)addr;
    (void)data;
    assert(0);
}

extern "C" void mrom_read(int32_t addr, int32_t *data) {
    *data = static_cast<int32_t>(Memory::readData(static_cast<std::uint32_t>(addr), 4));
}
