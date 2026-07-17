Chisel Project Template
=======================

Another version of the [Chisel template](https://github.com/ucb-bar/chisel-template) supporting mill.
mill is another Scala/Java build tool without obscure DSL like SBT. It is much faster than SBT.

Contents at a glance:

* `.gitignore` - helps Git ignore junk like generated files, build products, and temporary files.
* `build.mill` - instructs mill to build the Chisel project
* `Makefile` - rules to call mill
* `src/GCD.scala` - GCD source file
* `src/DecoupledGCD.scala` - another GCD source file
* `src/npc/Elaborate.scala` - emits SystemVerilog for the `Core` module
* `test/src/NpcUnitSpec.scala` - unit tests for branch, decode, and CSR behavior
* `test/src/GCDSpec.scala` - GCD tester

Feel free to rename or delete files under `src/` and `test/` or use them as a reference/template.

## Getting Started

First, install mill by referring to the documentation [here](https://com-lihaoyi.github.io/mill).

To run all tests in this design (recommended for test-driven development):
```bash
make test
```

To generate Verilog:
```bash
make verilog
```

## Verilator build modes

Development builds use FST waveforms by default:

```bash
make run DEFCONFIG=dev
```

Performance builds disable waveform support entirely:

```bash
make run DEFCONFIG=perf
```

Waveform support can be selected explicitly:

```bash
make run DEFCONFIG=dev WAVE=fst
make run DEFCONFIG=dev WAVE=none
```

Useful build and simulation tuning variables are:

```bash
make verilator-build BUILD_JOBS=0 SIM_THREADS=1
```

- `BUILD_JOBS=0` lets Verilator use the available CPUs for C++ compilation.
- `SIM_THREADS` controls generated-model runtime threads; small designs should
  benchmark `1` against `2` because scheduling overhead can outweigh gains.
