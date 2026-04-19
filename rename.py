import os
import re

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    def replacer(match):
        text = match.group(1)
        text = text.replace('bitchatters', 'BLEChatters')
        text = text.replace('Bitchatters', 'BLEChatters')
        text = text.replace('bitchat', 'BLEChat')
        text = text.replace('Bitchat', 'BLEChat')
        return f'>{text}<'

    new_content = re.sub(r'>(.*?)<', replacer, content, flags=re.DOTALL)
    
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Updated {filepath}")

res_dir = r"c:\Users\Mathew\Downloads\bitchat-android-main\bitchat-android-main\app\src\main\res"
for root, dirs, files in os.walk(res_dir):
    for file in files:
        if file == 'strings.xml':
            process_file(os.path.join(root, file))

print("Done")
