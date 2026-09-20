ENTRY(_start)

/* 由于 ysyxsoc 地址空间设计需要把 elf 文件的各个段拆分
 * mrom rx 且 resetpc = mrom_start, 因此放置 text rodata 段
 * data bss heap stack 涉及到读写操作, 应放到 sram 中
 */

MEMORY
{
  sram (rw) : ORIGIN = 0x0f000000, LENGTH = 8K
  mrom (rx) : ORIGIN = 0x20000000, LENGTH = 4K
}

SECTIONS
{
  .text : {
    KEEP(*(entry))
    *(.text*)
  } > mrom

  .rodata : {
    *(.rodata*)
  } > mrom

  .data : {
    . = ALIGN(4);
    _data_start = .;
    *(.data*)
    . = ALIGN(4);
    _data_end = .;
  } > sram AT > mrom

  _data_load_start = LOADADDR(.data);

  .bss : {
    . = ALIGN(4);
    _bss_start = .;
    *(.bss*)
    *(.sbss*)
    *(.scommon)
    . = ALIGN(4);
    _bss_end = .;
  } > sram

  _heap_start = ALIGN(16);

  _stack_pointer = ORIGIN(sram) + LENGTH(sram);

  ASSERT(_start == ORIGIN(mrom), "_start must be at MROM reset vector")

  ASSERT(_heap_start <= _stack_pointer, "SRAM overflow")
}

