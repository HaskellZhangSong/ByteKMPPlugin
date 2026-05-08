import re


def replace_jsfield_pattern(text):
    """
    匹配 JsField<pattern,"string"> 格式的字符串，用 pattern 替换整个内容
    :param text: 原始文本
    :return: 替换后的文本
    """
    # 正则表达式解析：
    # JsField\s*<      匹配 JsField 后接任意空格 + <
    # (.*?)            非贪婪匹配 pattern 部分（捕获组1）
    # \s*,\s*"[^"]+"   匹配 任意空格 + , + 任意空格 + "任意非引号字符"
    # \s*>             匹配任意空格 + >
    pattern = r'JsField\s*<(.*?)\s*(,\s*"[^"]+"\s*)+>'
    # 使用 re.sub 替换，\1 引用第一个捕获组（即 pattern 部分）
    result = re.sub(pattern, r'\1', text)
    return result
