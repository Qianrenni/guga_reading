class ClassProperty:
    def __init__(self, func):
        self.func = func

    def __get__(self, instance, owner):
        # owner 是类本身(如 MyClass)
        return self.func(owner)
