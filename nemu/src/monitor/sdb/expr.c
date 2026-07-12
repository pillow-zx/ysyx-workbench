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

#include <isa.h>

#ifdef CONFIG_SDB

/* We use the POSIX regex functions to process regular expressions.
 * Type 'man regex' for more information about POSIX regex functions.
 */
#include <limits.h>
#include <regex.h>
#include <memory/vaddr.h>

enum {
        TK_NOTYPE = 256,
        TK_EQ,

        /* TODO: Add more token types */
        TK_NEQ,
        TK_AND,
        TK_HEX,
        TK_DEC,
        TK_REG,
        TK_NEG,
        TK_DEREF,
};

#define TOKEN_LEN 32
#define MAX_TOKEN_NUM 1024

typedef struct {
        int type;
        char str[TOKEN_LEN];
} Token;

static Token tokens[MAX_TOKEN_NUM];
static int nr_token;

static struct Rule {
        const char *regex;
        int type;
} rules[] = {
        {" +", TK_NOTYPE},
        {"\\+", '+'},
        {"-", '-'},
        {"\\*", '*'},
        {"/", '/'},
        {"\\(", '('},
        {"\\)", ')'},
        {"==", TK_EQ},
        {"!=", TK_NEQ},
        {"&&", TK_AND},
        {"0[xX][0-9a-fA-F]+", TK_HEX},
        {"[0-9]+", TK_DEC},
        {"\\$(\\%0|ra|sp|gp|tp|t[0-6]|s([0-9]|10|11)|a[0-7])", TK_REG},
};

#define NR_REGEX ARRLEN(rules)
static regex_t re[NR_REGEX] = {};

void init_regex()
{
        int i;
        char error_msg[128];
        int ret;

        for (i = 0; i < NR_REGEX; i++) {
                ret = regcomp(&re[i], rules[i].regex, REG_EXTENDED);
                if (ret != 0) {
                        regerror(ret, &re[i], error_msg, 128);
                        panic("regex compilation failed: %s\n%s", error_msg,
                              rules[i].regex);
                }
        }
}

static bool is_operand(int type)
{
        switch (type) {
        case TK_DEC:
        case TK_HEX:
        case TK_REG:
        case ')':
                return true;
        default:
                return false;
        }
}

static bool is_unary_context(void)
{
        if (nr_token == 0)
                return true;
        return !is_operand(tokens[nr_token - 1].type);
}

static void add_token(int type, const char *start, int len)
{
        Assert(nr_token < MAX_TOKEN_NUM, "Too many tokens");
        tokens[nr_token].type = type;

        if (start != NULL) {
                Assert(len < TOKEN_LEN, "Token too long");
                memcpy(tokens[nr_token].str, start, len);
                tokens[nr_token].str[len] = '\0';
        }

        nr_token++;
}

static bool make_token(char *expr)
{
        nr_token = 0;
        int pos = 0;
        regmatch_t pmatch;

        while (expr[pos] != '\0') {
                bool matched = false;

                for (int i = 0; i < NR_REGEX; i++) {
                        if (regexec(&re[i], expr + pos, 1, &pmatch, 0) != 0)
                                continue;
                        if (pmatch.rm_so != 0)
                                continue;

                        matched = true;

                        int len = pmatch.rm_eo;

                        const char *text = expr + pos;

                        pos += len;

                        int type = rules[i].type;

                        if (type == TK_NOTYPE)
                                break;

                        if (type == '*' && is_unary_context())
                                type = TK_DEREF;

                        if (type == '-' && is_unary_context())
                                type = TK_NEG;

                        add_token(type, text, len);
                        break;
                }

                if (!matched) {
                        Log("Bad token near \"%s\"\n", expr + pos);
                        return false;
                }
        }

        return true;
}

static bool is_operator(int type)
{
        switch (type) {
        case '+':
        case '-':
        case '*':
        case '/':
        case TK_EQ:
        case TK_NEG:
        case TK_AND:
        case TK_NEQ:
        case TK_DEREF:
                return true;
        default:
                return false;
        }
}

static int precedence(int type)
{
        switch (type) {
        case TK_AND:
                return 1;
        case TK_EQ:
        case TK_NEQ:
                return 2;
        case '+':
        case '-':
                return 3;
        case '*':
        case '/':
                return 4;
        case TK_NEG:
        case TK_DEREF:
                return 5;
        default:
                return INT_MAX;
        }
}

static bool check_parentheses(int l, int r)
{
        if (tokens[l].type != '(' || tokens[r].type != ')')
                return false;

        int depth = 0;

        for (int i = l; i <= r; i++) {
                if (tokens[i].type == '(')
                        depth++;
                else if (tokens[i].type == ')')
                        depth--;

                if (depth == 0 && i < r)
                        return false;

                if (depth < 0)
                        return false;
        }

        return depth == 0;
}

static int find_main_operator(int l, int r)
{
        int depth = 0;

        int op = -1;

        int lowest = INT_MAX;

        for (int i = l; i <= r; i++) {
                int type = tokens[i].type;

                if (type == '(') {
                        depth++;
                        continue;
                }

                if (type == ')') {
                        depth--;
                        continue;
                }

                if (depth != 0)
                        continue;

                if (!is_operator(type))
                        continue;

                int p = precedence(type);

                if (p <= lowest) {
                        lowest = p;

                        op = i;
                }
        }

        return op;
}

static word_t eval_atom(int p)
{
        word_t value = 0;
        switch (tokens[p].type) {
        case TK_DEC:
                sscanf(tokens[p].str, "%u", &value);
                return value;
        case TK_HEX:
                sscanf(tokens[p].str, "%x", &value);
                return value;
        case TK_REG:
                bool success = false;
                value = isa_reg_str2val(tokens[p].str + 1, &success);
                Assert(success, "Unknown register");
                return value;
        default:
                Assert(false, "Invalid atom");
        }

        return 0;
}

static word_t eval_unary(int op, word_t rhs)
{
        switch (tokens[op].type) {
        case TK_NEG:
                return -rhs;
        case TK_DEREF:
                return vaddr_read(rhs, 4);
        default:
                Assert(false, "Unknown unary operator");
        }

        return 0;
}

static int eval_binary(int op, int lhs, int rhs)
{
        switch (tokens[op].type) {
        case '+':
                return lhs + rhs;
        case '-':
                return lhs - rhs;
        case '*':
                return lhs * rhs;
        case '/':
                Assert(rhs != 0, "Division by zero");
                return lhs / rhs;
        case TK_EQ:
                return lhs == rhs;
        case TK_NEQ:
                return lhs != rhs;
        case TK_AND:
                return lhs && rhs;
        default:
                Assert(false, "Unknown binary operator");
        }
        return 0;
}

static int eval(int l, int r)
{
        if (l > r)
                Assert(false, "Bad expression");

        if (l == r)
                return eval_atom(l);

        if (check_parentheses(l, r))
                return eval(l + 1, r - 1);

        int op = find_main_operator(l, r);

        Assert(op != -1, "Cannot find operator");

        if (tokens[op].type == TK_NEG || tokens[op].type == TK_DEREF) {
                word_t rhs = eval(op + 1, r);

                return eval_unary(op, rhs);
        }

        int lhs = eval(l, op - 1);

        int rhs = eval(op + 1, r);

        return eval_binary(op, lhs, rhs);
}

word_t expr(char *e, bool *success)
{
        *success = false;
        if (!make_token(e))
                return 0;

        if (nr_token == 0)
                return 0;

        word_t result = eval(0, nr_token - 1);

        *success = true;

        return result;
}

#endif
