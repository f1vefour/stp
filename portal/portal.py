# A very forgetful server to hold the cookie data until the other
# browser receives it. Can be made to run on just about any setup.

from google.appengine.api import memcache
import random

_CACHE_EXPIRY = 300 # 3 minute expiry
_MAX_OBJ_SIZE = 40000 # 40K of cookies is a lot of cookies!
_KEY_SIZE = 10
_KEY_CHAR = 'abcdefghijklmnopqrstuvwxyz-.'

def _set(k, v):
  memcache.set(k, v, time=_CACHE_EXPIRY)

def _get(k):
  return memcache.get(k)

def portalapp(environ, start_response):

  headers = [('Content-Type', 'text/plain; charset=UTF-8')]
  path = environ['PATH_INFO']
  method = environ['REQUEST_METHOD']

  if method == 'POST' and path == '/d/':
    rnd = random.SystemRandom()
    key = ''.join(
      _KEY_CHAR[rnd.randint(0, len(_KEY_CHAR) - 1)]
      for _ in xrange(_KEY_SIZE)
    )
    value = environ['wsgi.input'].read(_MAX_OBJ_SIZE)
    _set(key, value)
    start_response('200 OK', headers)
    return[key]

  if method == 'GET' and path.startswith('/d/') \
     and len(path) == 3 + _KEY_SIZE:

    key = path[3:]
    value = _get(key)
    if value is not None:
      start_response('200 OK', headers)
      return [value]

  start_response('404 Not found', headers)
  return [""]
