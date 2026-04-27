def rand():
    yield 1
    print(12)


def main():
    # 用next调用生成器
    ge = rand()
    print(next(ge))  # 输出 1
    try:
        print(next(ge))  # 输出 12
    except StopIteration:
        print("Generator is exhausted")


if __name__ == "__main__":
    main()
