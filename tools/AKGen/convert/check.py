kotlin_keyword = {"abstract", "as", "break", "class", "continue", "do", "else"
                , "false", "for" , "fun", "if", "in", "interface", "is", "null"
                , "object", "package", "return", "super", "this", "throw", "true"
                , "try", "typealias", "val", "var", "when", "while"}
def check_kotlin_class_name(name : str) -> None:
    # 检查是否为空
    if not name or not name.strip():
        raise ValueError("[Error 1001] Class name cannot be empty")
    
    # 移除首尾空格
    name = name.strip()
    
    # 检查是否为 Kotlin 关键字
    if name in kotlin_keyword:
        raise ValueError(f"[Error 1001] Class name '{name}' is a Kotlin keyword")
    
    # 检查第一个字符必须是字母或下划线
    if not (name[0].isalpha() or name[0] == '_'):
        raise ValueError(f"[Error 1001] Class name '{name}' must start with a letter or underscore")
    
    # 检查后续字符必须是字母、数字或下划线
    for char in name[1:]:
        if not (char.isalnum() or char == '_'):
            raise ValueError(f"[Error 1001] Class name '{name}' contains invalid character '{char}'. Only letters, digits, and underscores are allowed")
    
def check_kotlin_package_name(name : str) -> None:
    # 检查是否为空
    if not name or not name.strip():
        raise ValueError("[Error 1002] Package name cannot be empty")
    
    # 移除首尾空格
    name = name.strip()
    
    # 检查是否以点号开头或结尾
    if name.startswith('.') or name.endswith('.'):
        raise ValueError(f"[Error 1002] Package name '{name}' cannot start or end with a dot")
    
    # 检查是否有连续的点号
    if '..' in name:
        raise ValueError(f"[Error 1002] Package name '{name}' cannot contain consecutive dots")
    
    # 分割包名并检查每个部分
    parts = name.split('.')
    for part in parts:
        # 检查每个部分是否为空
        if not part:
            raise ValueError(f"[Error 1002] Package name '{name}' contains empty segment")
        
        # 检查是否为 Kotlin 关键字
        if part in kotlin_keyword:
            raise ValueError(f"[Error 1002] Package name segment '{part}' in '{name}' is a Kotlin keyword")
        
        # 检查第一个字符必须是字母或下划线
        if not (part[0].isalpha() or part[0] == '_'):
            raise ValueError(f"[Error 1002] Package name segment '{part}' in '{name}' must start with a letter or underscore")
        
        # 检查后续字符必须是字母、数字或下划线
        for char in part[1:]:
            if not (char.isalnum() or char == '_'):
                raise ValueError(f"[Error 1002] Package name segment '{part}' in '{name}' contains invalid character '{char}'. Only letters, digits, and underscores are allowed")

def check_kotlin_field_name(name : str) -> None:
    # 检查是否为空
    if not name or not name.strip():
        raise ValueError("[Error 2001] Field name cannot be empty")
    
    # 移除首尾空格
    name = name.strip()
    
    # 如果字段名用反引号包裹，提取内部名称
    actual_name = name
    if name.startswith('`') and name.endswith('`'):
        if len(name) <= 2:
            raise ValueError("[Error 2001] Field name cannot be empty within backticks")
        actual_name = name[1:-1]
        # 对于反引号包裹的名称，只检查是否为空，不检查其他规则
        if not actual_name:
            raise ValueError("[Error 2001] Field name cannot be empty within backticks")
        return
    
    # 检查第一个字符必须是字母或下划线
    if not (actual_name[0].isalpha() or actual_name[0] == '_'):
        raise ValueError(f"[Error 2001] Field name '{actual_name}' must start with a letter or underscore")
    
    # 检查后续字符必须是字母、数字或下划线
    for char in actual_name[1:]:
        if not (char.isalnum() or char == '_'):
            raise ValueError(f"[Error 2001] Field name '{actual_name}' contains invalid character '{char}'. Only letters, digits, and underscores are allowed")
