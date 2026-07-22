#include <cassert>
#include <device.hh>
#include <stdexcept>
#include <iostream>
#include <print>

auto SerialDevice::read(std::uint64_t offset, std::size_t len) const -> std::uint64_t {
    throw std::runtime_error("Serail not support read");
}

auto SerialDevice::write(const std::uint64_t offset, const std::size_t len, const std::uint64_t data) -> void {
    throw std::runtime_error("Should not use uart in cpp");
    assert(len == 1);

    switch (offset) {
        case 0:
            std::cout << static_cast<char>(data);
            std::cout.flush();
            break;
        default:
            throw std::runtime_error("Unknown UART register");
    }
}
