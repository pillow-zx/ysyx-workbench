#ifndef MMIO_H
#define MMIO_H

#define DEVICE_BASE     0x10000000
// 因为 TXR 偏移为 0, 此处可以直接不加
#define UART_BASE       (DEVICE_BASE + 0x00000fff)

#endif
