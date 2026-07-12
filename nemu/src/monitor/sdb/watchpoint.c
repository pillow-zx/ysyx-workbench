/***************************************************************************************
 * Copyright (c) 2014-2024 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan
 * PSL v2. You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
 * KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include "sdb.h"

#ifdef CONFIG_WATCHPOINT

#define NR_WP 32
#define MAX_EXPR_LEN 64

typedef struct watchpoint {
        int NO;
        struct watchpoint *next;

        /* TODO: Add more members if necessary */
        char str[MAX_EXPR_LEN];
        word_t value;
} WP;

static WP wp_pool[NR_WP] = {};
static WP *head = NULL, *free_ = NULL;

void init_wp_pool()
{
        int i;
        for (i = 0; i < NR_WP; i++) {
                wp_pool[i].NO = i;
                wp_pool[i].next = (i == NR_WP - 1 ? NULL : &wp_pool[i + 1]);
        }

        head = NULL;
        free_ = wp_pool;
}

/* TODO: Implement the functionality of watchpoint */
WP *new_wp(char *str, bool *success)
{
        WP *wp = free_;
        free_ = free_->next;
        strcpy(wp->str, str);
        wp->str[strlen(str)] = '\0';
        wp->value = expr(str, success);
        wp->next = head;
        head = wp;
        return wp;
}

void add_wp(char *str)
{
        if (free_ == NULL) {
                printf("No free watchpoint avaiable!\n");
                return;
        }

        bool success = true;
        WP *wp = new_wp(str, &success);
        if (!success) {
                printf("Invalid expression: %s\n", str);
                return;
        }

        printf("Watchpoint %d: %s, value = %d\n", wp->NO, wp->str, wp->value);
}

void delete_wp(word_t position)
{
        if (head->NO == position) {
                WP *temp = head;
                head = head->next;
                temp->next = free_;
                free_ = temp;
        } else {
                WP *wp = head;
                while (wp->next) {
                        if (wp->next->NO == position) {
                                WP *temp = wp->next;
                                wp->next = temp->next;
                                temp->next = free_;
                                free_ = temp;
                                break;
                        }
                        wp = wp->next;
                }
        }
}

void display_wp()
{
        if (head == NULL) {
                printf("No watchpoint set.\n");
                return;
        }
        WP *temp = head;
        while (temp) {
                printf("Watchpoint %d: %s, value: %u\n", temp->NO, temp->str,
                    temp->value);
                temp = temp->next;
        }
}

int update_wp()
{
        int is_changed = 0;
        WP *wp = head;
        while (wp != NULL) {
                bool success = true;
                word_t value = expr(wp->str, &success);
                Assert(success, "Invalid expression: %s", wp->str);

                if (value != wp->value) {
                        is_changed += 1;
                        printf("Watchpoint %d: %s changed from %d to %d\n",
                               wp->NO, wp->str, wp->value, value);
                        wp->value = value; // 更新监视点的值
                }
                wp = wp->next;
        }
        return is_changed;
}

#endif
