#include <algorithm>
#include <fstream>
#include <iomanip>
#include <iostream>

#include <mm.hh>

auto Memory::getBaseAddress() -> std::size_t {
    return MEMORYSTART;
}

auto Memory::init(const std::string &filename) -> bool {
    std::fill(memory.begin(), memory.end(), 0);
    return loadProgram(filename);
}

auto Memory::fetchInst(const std::uint32_t addr) -> std::uint32_t {
    return readData(addr, sizeof(std::uint32_t));
}

auto Memory::readData(const std::uint32_t addr, const std::uint32_t size) -> std::uint32_t {
    if (size == 0 || size > sizeof(std::uint32_t) || addr < MEMORYSTART) {
        return 0;
    }

    const auto offset = static_cast<std::size_t>(addr - MEMORYSTART);
    if (offset > MEMORYSIZE || size > MEMORYSIZE - offset) {
        return 0;
    }

    std::uint32_t data = 0;
    for (std::size_t index = 0; index < size; ++index) {
        data |= static_cast<std::uint32_t>(memory[offset + index]) << (index * 8);
    }
    return data;
}

auto Memory::writeData(const std::uint32_t addr, const std::uint32_t data, const std::uint32_t wmask) -> void {
    if (addr < MEMORYSTART) {
        return;
    }

    const auto offset = static_cast<std::size_t>(addr - MEMORYSTART);
    for (std::size_t lane = 0; lane < sizeof(std::uint32_t); ++lane) {
        if ((wmask & (1U << lane)) == 0 || offset + lane >= MEMORYSIZE) {
            continue;
        }
        memory[offset + lane] = static_cast<std::uint8_t>(data >> (lane * 8));
    }
}

auto Memory::showMemory(const std::uint32_t addr, const std::uint32_t len) -> void {
    if (len == 0) {
        std::cout << "Error: Length must be greater than 0." << std::endl;
        return;
    }

    constexpr std::uint64_t wordSize = sizeof(std::uint32_t);
    const auto begin = static_cast<std::uint64_t>(addr);
    const auto end = begin + static_cast<std::uint64_t>(len) * wordSize;
    if (constexpr auto memoryEnd = static_cast<std::uint64_t>(MEMORYSTART) + MEMORYSIZE;
        begin < MEMORYSTART || end > memoryEnd) {
        std::cout << "Error: Address range out of bounds." << std::endl;
        return;
    }

    const auto flags = std::cout.flags();
    const auto fill = std::cout.fill();
    for (std::uint32_t index = 0; index < len; ++index) {
        const auto current = addr + index * static_cast<std::uint32_t>(wordSize);
        std::cout << "0x" << std::hex << std::setw(8) << std::setfill('0') << current << ": 0x"
                << std::setw(8) << readData(current, wordSize) << '\n';
    }
    std::cout.flags(flags);
    std::cout.fill(fill);
}

auto Memory::loadProgram(const std::string &filename) -> bool {
    if (filename.empty()) {
        for (std::size_t word = 0; word < img.size(); ++word) {
            for (std::size_t byte = 0; byte < sizeof(img[word]); ++byte) {
                memory[word * sizeof(img[word]) + byte] =
                        static_cast<std::uint8_t>(img[word] >> (byte * 8));
            }
        }
        return true;
    }

    std::ifstream file(filename, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        std::cout << "Failed to open file " << filename << std::endl;
        return false;
    }

    const auto end = file.tellg();
    if (end < 0 || static_cast<std::uint64_t>(end) > MEMORYSIZE) {
        std::cout << "File size exceeds memory size." << std::endl;
        return false;
    }

    const auto fileSize = static_cast<std::size_t>(end);
    file.seekg(0, std::ios::beg);
    if (!file.read(reinterpret_cast<char *>(memory.data()), static_cast<std::streamsize>(fileSize))) {
        std::cout << "Failed to read file " << filename << std::endl;
        return false;
    }
    return true;
}
