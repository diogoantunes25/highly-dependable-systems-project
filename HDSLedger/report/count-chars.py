import re

file_path = 'report-1.tex'

with open(file_path, 'r', encoding='utf-8') as file:
    content = file.read()

# Use regex to find all occurrences of \section{...}
# section_matches = re.finditer(r'\\section\{(.*?)\}([\s\S]*?)(?=\\section|\Z)', content)
# Use regex to find all occurrences of \section{...}, \subsection{...}, and \subsubsection{...}
section_matches = re.finditer(r'(\\section|\\subsection|\\subsubsection)\{(.*?)\}([\s\S]*?)(?=\\section|\\subsection|\\subsubsection|\Z)', content)

total_character_count = 0

for match in section_matches:
    section_type = match.group(1)
    section_title = match.group(2)
    section_content = match.group(3)
    
    # Remove white spaces (including spaces, tabs, and newlines)
    section_title_without_spaces = re.sub(r'\s', '', section_title)
    section_content_without_spaces = re.sub(r'\s', '', section_content)
    
    # Count the characters for title and content within the section
    section_title_character_count = len(section_title_without_spaces)
    section_content_character_count = len(section_content_without_spaces)
    
    # Print the count for each section
    print(f"{section_type} '{section_title}':")
    print(f"  Title: {section_title_character_count} characters")
    print(f"  Text: {section_content_character_count} characters")
    
    # Add to the total count
    total_character_count += section_title_character_count + section_content_character_count

# Print the total count for all sections
print(f"\nTotal characters (excluding white spaces) in '{file_path}': {total_character_count}")
