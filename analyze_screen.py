import sys
from PIL import Image

def analyze(file_path):
    img = Image.open(file_path)
    img = img.convert('RGB')
    width, height = img.size
    
    colors = img.getcolors(width * height)
    colors = sorted(colors, key=lambda x: x[0], reverse=True)
    
    print(f"Size: {width}x{height}")
    print("Top 10 colors:")
    for count, color in colors[:10]:
        print(f"Color: {color}, Count: {count}, Percentage: {count / (width * height) * 100:.2f}%")

if __name__ == '__main__':
    analyze(sys.argv[1])
