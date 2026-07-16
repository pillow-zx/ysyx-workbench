VERILATOR = verilator
PYTHON ?= python3
VERILATOR_CFLAGS += -MMD --build --cc --trace-vcd \
	-O2 --x-assign fast --x-initial fast --noassert

TOPNAME = Core
PROJECT_NAME = npc

BUILD_DIR = build
OBJ_DIR = $(BUILD_DIR)/obj_dir
RTL = $(addprefix $(BUILD_DIR)/,$(shell cat $(BUILD_DIR)/filelist.f 2>/dev/null))
BIN = $(OBJ_DIR)/V$(TOPNAME)

VERILATOR_SRC_DIR = src/verilator
SRC = $(shell find $(VERILATOR_SRC_DIR) -name '*.cc')
AUTOCONFIG_SCRIPT = $(VERILATOR_SRC_DIR)/config/generate_config.py

INC_PATH ?= $(abspath $(VERILATOR_SRC_DIR)/include)
INCFLAGS = $(addprefix -I,$(INC_PATH))
CXXFLAGS += $(INCFLAGS) -std=c++17 -O2

CAPSTONE_CFLAGS = $(shell pkg-config --cflags capstone)
CAPSTONE_LIBS = $(shell pkg-config --libs capstone)
CXXFLAGS += $(CAPSTONE_CFLAGS)
LDFLAGS += $(CAPSTONE_LIBS)

ARGS ?=
DEFCONFIG ?=

default: all

all: sim

test:
	mill -i test

verilog:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mill -i runMain npc.Elaborate --target-dir $(BUILD_DIR)

autoconfig:
	$(PYTHON) $(AUTOCONFIG_SCRIPT) --defconfig "$(DEFCONFIG)"

verilator-build: autoconfig verilog $(SRC)
	@echo "Building $(PROJECT_NAME) with Verilator..."
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $(RTL) $(SRC) \
		$(addprefix -CFLAGS ,$(CXXFLAGS)) $(addprefix -LDFLAGS ,$(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe

sim: verilator-build
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Running simulation with args: $(ARGS)"
	$(BIN) $(ARGS)

# ONLY USED TO TEST
run:
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

.PHONY: default all test verilog autoconfig verilator-build sim run help reformat checkformat bsp idea clean

-include ../Makefile
