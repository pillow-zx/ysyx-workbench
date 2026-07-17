#include <wave.hh>

#include "VCore.h"
#include "verilated.h"
#include "verilated_fst_c.h"

#include <memory>

namespace {

constexpr int TraceDepth = 5;

class FstWaveWriter final : public WaveWriter {
public:
    auto attach(VCore &top) -> void override {
        top.trace(&trace_, TraceDepth);
    }

    auto open(const std::string &path) -> void override {
        trace_.open(path.c_str());
    }

    auto close() -> void override {
        trace_.close();
    }

    auto dump(const std::uint64_t time) -> void override {
        trace_.dump(time);
    }

private:
    VerilatedFstC trace_;
};

} // namespace

auto createWaveWriter(const bool enabled) -> std::unique_ptr<WaveWriter> {
    if (!enabled) {
        return {};
    }

    Verilated::traceEverOn(true);
    return std::make_unique<FstWaveWriter>();
}
