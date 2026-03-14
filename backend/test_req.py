import urllib.request
import urllib.error
import json

req = urllib.request.Request(
    'http://127.0.0.1:8000/accounts/signup/',
    data=json.dumps({'username': 'a', 'email':'a@a.com', 'password':'a'}).encode(),
    headers={'Content-Type': 'application/json'}
)

try:
    resp = urllib.request.urlopen(req)
    print(resp.read().decode())
except urllib.error.HTTPError as e:
    with open('error_out.html', 'wb') as f:
        f.write(e.read())
    print("Error saved to error_out.html")
