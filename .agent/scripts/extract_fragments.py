import os

html_path = 'c:/Users/Ashley/OneDrive/Documents/CivicLedger/cityledger/src/main/resources/templates/index.html'
with open(html_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Find navbar bounds
nav_start = -1
nav_end = -1
for i, line in enumerate(lines):
    if 'class="navbar-absolute w-nav"' in line:
        nav_start = i
    if 'home-2-banner-section' in line:
        nav_end = i - 1 # End before the next section
        break

# Find footer bounds
footer_start = -1
footer_end = -1
for i, line in enumerate(lines):
    if 'class="footer"' in line:
        footer_start = i
    if '</body>' in line:
        footer_end = i - 1
        break

navbar_html = ''.join(lines[nav_start:nav_end])
footer_html = ''.join(lines[footer_start:footer_end])

os.makedirs('c:/Users/Ashley/OneDrive/Documents/CivicLedger/cityledger/src/main/resources/templates/fragments', exist_ok=True)

with open('c:/Users/Ashley/OneDrive/Documents/CivicLedger/cityledger/src/main/resources/templates/fragments/navbar.html', 'w', encoding='utf-8') as f:
    f.write('<!DOCTYPE html>\n<html xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/extras/spring-security">\n<head></head>\n<body>\n<div th:fragment="navbar">\n')
    f.write(navbar_html)
    f.write('</div>\n</body>\n</html>\n')

with open('c:/Users/Ashley/OneDrive/Documents/CivicLedger/cityledger/src/main/resources/templates/fragments/footer.html', 'w', encoding='utf-8') as f:
    f.write('<!DOCTYPE html>\n<html xmlns:th="http://www.thymeleaf.org">\n<head></head>\n<body>\n<div th:fragment="footer">\n')
    f.write(footer_html)
    f.write('</div>\n</body>\n</html>\n')

# Now replace in index.html
new_lines = lines[:nav_start] + ['    <div th:replace="~{fragments/navbar :: navbar}"></div>\n'] + lines[nav_end:footer_start] + ['    <div th:replace="~{fragments/footer :: footer}"></div>\n'] + lines[footer_end:]

with open(html_path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

print('Extracted navbar and footer successfully.')
