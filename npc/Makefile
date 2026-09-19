VERILATOR = verilator
PYTHON ?= python3

TOPNAME = ysyxSoCFull
PROJECT_NAME = npc

BUILD_DIR = build
OBJ_DIR = $(BUILD_DIR)/obj_dir
BIN = $(OBJ_DIR)/V$(TOPNAME)
FILELIST = $(BUILD_DIR)/filelist.f
BUILD_SIGNATURE = $(BUILD_DIR)/.verilator-build-signature

VERILATOR_SRC_DIR = src/verilator
WAVE_SRC_DIR = $(VERILATOR_SRC_DIR)/wave
CHISEL_SRC = $(shell find src -name '*.scala' | sort)
AUTOCONFIG_SCRIPT = $(VERILATOR_SRC_DIR)/config/generate_config.py
AUTOCONFIG_HEADER = $(VERILATOR_SRC_DIR)/include/generated/autoconfig.hh

YSYXSOC_VERILOG = $(shell find $(YSYXSOC_HOME)/perip -name '*.v' | sort)
YSYXSOC_FULL = $(YSYXSOC_HOME)/build/ysyxSoCFull.v

INC_PATH ?= $(abspath $(VERILATOR_SRC_DIR)/include) \
            $(YSYXSOC_HOME)/perip/uart16550/rtl \
            $(YSYXSOC_HOME)/perip/spi/rtl
INCFLAGS = $(addprefix -I,$(INC_PATH))
CXXFLAGS += $(INCFLAGS) -std=c++23 -O3 -pthread

CAPSTONE_CFLAGS = $(shell pkg-config --cflags capstone)
CAPSTONE_LIBS = $(shell pkg-config --libs capstone)
CXXFLAGS += $(CAPSTONE_CFLAGS)
LDFLAGS += $(CAPSTONE_LIBS)

ARGS ?=
DEFCONFIG ?=
WAVE ?= $(if $(filter perf perf.toml,$(DEFCONFIG)),none,fst)
BUILD_JOBS ?= 0
SIM_THREADS ?= 1

ifeq ($(WAVE),fst)
TRACE_FLAGS = --trace-fst
WAVE_SRC = $(WAVE_SRC_DIR)/fst.cc
else ifeq ($(WAVE),none)
TRACE_FLAGS =
WAVE_SRC = $(WAVE_SRC_DIR)/none.cc
else
$(error WAVE must be one of: fst, none)
endif

SRC = $(shell find $(VERILATOR_SRC_DIR) -name '*.cc' ! -path '$(WAVE_SRC_DIR)/*' | sort) $(WAVE_SRC)

VERILATOR_MODEL_FLAGS = -MMD --cc $(TRACE_FLAGS) \
	--timescale "1ns/1ns" --no-timing \
	-O3 --x-assign fast --x-initial fast --noassert \
	--output-split 20000 --threads $(SIM_THREADS)
VERILATOR_CFLAGS += --build -j $(BUILD_JOBS) $(VERILATOR_MODEL_FLAGS)

BUILD_OPTIONS = $(VERILATOR_MODEL_FLAGS) | $(CXXFLAGS) | $(LDFLAGS)

default: all

all: sim

test:
	mill -i test

verilog: $(FILELIST)

$(FILELIST): $(CHISEL_SRC) build.mill $(YSYXSOC_VERILOG) $(YSYXSOC_FULL) | $(BUILD_DIR)
	$(call git_commit, "generate verilog")
	mill -i runMain npc.Elaborate --target-dir $(BUILD_DIR)
	@printf '%s\n' $(YSYXSOC_VERILOG) $(YSYXSOC_FULL) >> $@

autoconfig: $(AUTOCONFIG_HEADER)

$(AUTOCONFIG_HEADER): FORCE
	$(PYTHON) $(AUTOCONFIG_SCRIPT) --defconfig "$(DEFCONFIG)"

$(BUILD_DIR):
	mkdir -p $@

$(BUILD_SIGNATURE): FORCE | $(BUILD_DIR)
	@printf '%s\n' '$(BUILD_OPTIONS)' > $@.tmp
	@if cmp -s $@.tmp $@; then rm -f $@.tmp; else mv $@.tmp $@; fi

verilator-build: $(BIN)

$(BIN): $(FILELIST) $(SRC) $(AUTOCONFIG_HEADER) $(BUILD_SIGNATURE) Makefile
	@echo "Building $(PROJECT_NAME) with Verilator..."
	$(VERILATOR) $(VERILATOR_CFLAGS) $(INCFLAGS) \
		--top-module $(TOPNAME) -F $(FILELIST) $(SRC) \
		$(addprefix -CFLAGS ,$(CXXFLAGS)) $(addprefix -LDFLAGS ,$(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe

sim: verilator-build
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Running simulation with args: $(ARGS)"
	$(BIN) $(ARGS)

run: verilator-build
	@echo "Running simulation with args: $(ARGS)"
	$(BIN) $(ARGS)

help:
	mill -i runMain npc.Elaborate --help

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

bsp:
	mill -i mill.bsp.BSP/install

idea:
	mill -i mill.idea.GenIdea/idea

clean:
	@rm -rf $(BUILD_DIR)

.PHONY: default all test verilog autoconfig verilator-build sim run help reformat checkformat bsp idea clean FORCE

FORCE:

-include ../Makefile
