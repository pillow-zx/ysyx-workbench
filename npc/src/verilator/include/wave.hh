#pragma once

#include <cstdint>
#include <memory>
#include <string>

class VCore;

class WaveWriter {
public:
    virtual ~WaveWriter() = default;

    virtual auto attach(VCore &top) -> void = 0;
    virtual auto open(const std::string &path) -> void = 0;
    virtual auto close() -> void = 0;
    virtual auto dump(std::uint64_t time) -> void = 0;
};

[[nodiscard]] auto createWaveWriter(bool enabled) -> std::unique_ptr<WaveWriter>;
