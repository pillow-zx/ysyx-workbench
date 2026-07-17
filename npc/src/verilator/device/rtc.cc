#include <device.hh>
#include <ctime>
#include <stdexcept>

auto RtcDevice::read(const std::uint64_t offset, const std::size_t len) const -> std::uint64_t {
    if (len != 4) {
        throw std::runtime_error("RTC register width must be 4 bytes");
    }

    const auto us = upTime();

    switch (offset) {
        case 0:
            return static_cast<uint32_t>(us);
        case 4:
            return static_cast<uint32_t>(us >> 32);
        default:
            throw std::runtime_error("Unknown RTC register");
    }
}

auto RtcDevice::write(std::uint64_t offset, std::size_t len, std::uint64_t data) -> void {
    throw std::runtime_error("RTC is read only");
}

auto RtcDevice::hostTime() -> std::uint64_t {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<uint64_t>(ts.tv_sec) * 1'000'000ULL + static_cast<uint64_t>(ts.tv_nsec) / 1000;
}

auto RtcDevice::upTime() const -> std::uint64_t {
    return hostTime() - bootTime_;
}
