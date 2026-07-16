#include <options.hh>

#include <cstdlib>

#include <CLI/CLI.hpp>

auto parseOptions(const int argc, char *argv[]) -> NpcOptions {
    NpcOptions options;

    CLI::App app{"NPC simulator"};
    app.add_option("-i,--image", options.image, "Path to the program image");
    app.add_option("-l,--log", options.log, "Path to the instruction trace file");
    app.add_option("-e,--elf", options.elf, "Elf file of the image to trace funcation");
    app.add_flag("-b,--batch", options.batch, "Run without entering the monitor");

    try {
        app.parse(argc, argv);
    } catch (const CLI::ParseError &error) {
        std::exit(app.exit(error));
    }
    return options;
}
