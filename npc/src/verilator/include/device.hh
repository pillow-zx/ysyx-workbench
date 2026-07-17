#ifndef NPC_DEVICE_HH
#define NPC_DEVICE_HH

#include <string>
#include <cstdint>

class Device {
public:
    virtual ~Device() = default;

    virtual auto read(std::uint64_t offset, std::size_t len) const -> std::uint64_t = 0;

    virtual auto write(std::uint64_t offset, std::size_t len, std::uint64_t data) -> void = 0;
};

class SerialDevice: public Device {
public:
    [[nodiscard]] auto read(std::uint64_t offset, std::size_t len) const -> std::uint64_t override;

    auto write(std::uint64_t offset, std::size_t len, std::uint64_t data) -> void override;
};

class RtcDevice: public Device {
public:
    RtcDevice() : bootTime_(hostTime()) {}

    [[nodiscard]] auto read(std::uint64_t offset, std::size_t len) const -> std::uint64_t override;

    auto write(std::uint64_t offset, std::size_t len, std::uint64_t data) -> void override;

private:
    std::uint64_t bootTime_{0};

    [[nodiscard]] static auto hostTime() -> std::uint64_t;

    [[nodiscard]] auto upTime() const -> std::uint64_t;
};


#endif //NPC_DEVICE_HH
