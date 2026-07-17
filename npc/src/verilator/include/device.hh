#ifndef NPC_DEVICE_HH
#define NPC_DEVICE_HH

#include <chrono>
#include <cstdint>
#include <string>

class Device {
public:
    virtual ~Device() = default;

    virtual auto read(std::uint64_t offset, std::size_t len) const -> std::uint64_t = 0;

    virtual auto write(std::uint64_t offset, std::size_t len, std::uint64_t data) -> void = 0;
};

class SerialDevice : public Device {
public:
    [[nodiscard]] auto read(std::uint64_t offset, std::size_t len) const -> std::uint64_t override;

    auto write(std::uint64_t offset, std::size_t len, std::uint64_t data) -> void override;
};

class RtcDevice : public Device {
public:
    RtcDevice() = default;

    [[nodiscard]] auto read(std::uint64_t offset, std::size_t len) const -> std::uint64_t override;

    auto write(std::uint64_t offset, std::size_t len, std::uint64_t data) -> void override;

private:
    using Clock = std::chrono::steady_clock;
    using Microseconds = std::chrono::microseconds;

    Clock::time_point bootTime_{Clock::now()};

    [[nodiscard]] auto upTime() const -> std::uint64_t;
};

#endif // NPC_DEVICE_HH
