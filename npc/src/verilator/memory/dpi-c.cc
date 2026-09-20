#include <cassert>
#include <cstdint>

extern "C" void flash_read(int32_t addr, int32_t *data) {
    (void)addr;
    (void)data;
    assert(0);
}

extern "C" void mrom_read(int32_t addr, int32_t *data) {
    (void)addr;
    *data = 0x00100073; // ebreak
}
