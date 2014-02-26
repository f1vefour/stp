(function (window, undefined) {

  // XXX If you decide to set your own server up, change this URL

  var BASE_URL = 'http://sessionteleporter.appspot.com/d/';
  var openButton = document.getElementById('open');
  var keyInput = document.getElementById('key');

  var showMsg = function (msg) {
    document.getElementById('msg').textContent = msg;
  }

  var ajaxDone = function (password, emsg) {

    // Using the simplest method of decryption that SJCL provides us
    // with. This way, mistakes are minimized.

    try {
      // This is the only call out of this script
      // If you trust the authors of SJCL, just run a checksum on
      // sjcl.js in this directory and compare with the official one as
      // of commit a03ea8ef32329bc8d7bc28a438372b5acb46616b
      // If not, then enjoy auditing a minified crypto library!
      var dmsg = sjcl.decrypt(password, emsg);
    } catch (e) {
      showMsg("Bad key");
      return;
    }

    // URL and cookies are separated by a new line character

    var m = /^([^\n]+)\n([^\n]+)\n(.*)$/.exec(dmsg);
    if (!m) {
      showMsg("Weird ass error");
      return;
    }
    var url = m[1], domain = m[2], cookies = m[3].split(';');

    var urlBase = (/^https?:\/\/[^\/]+\//i.exec(url) || [null])[0];
    if (!urlBase) {
      showMsg("Weird ass error");
      return;
    }

    // Fill the cookie jar with new dangerous ones!

    cookies.forEach(function (el) {
      var kv = /^([^=]+)=(.*)$/.exec(el.trim());
      if (!kv)
        return;
      teleCookie = {
        url: urlBase,
        name: kv[1],
        value: kv[2],
        domain: '.' + domain,
        path: '/'
      };
      chrome.cookies.set(teleCookie);
    });

    // Open a new tab and navigate to the (logged in) session

    chrome.tabs.create({
      url: url,
      active: true
    });
    window.close();

  };

  document.querySelector('form').addEventListener(
    'submit',
    function(e) {

      e.preventDefault(true);

      // Get the password, docId and verify the format
      // FIXME hardcoded minimum password length

      var m = /^([a-z.-]{8,})\/(.+)$/.exec(keyInput.value);
      if (!m) {
        showMsg("Bad key");
        return;
      }
      var password = m[1], docId = m[2];

      openButton.disabled = true;
      showMsg("Loading …");

      // Download the encrypted message

      var ajaxReq = new XMLHttpRequest();
      ajaxReq.addEventListener('load', function () {

        if (ajaxReq.status >= 200 && ajaxReq.status < 400) {
          ajaxDone(password, ajaxReq.responseText);

        } else if (ajaxReq.status == 404) {
          showMsg("Bad key");

        } else {
          showMsg("Server error");
        }

        openButton.disabled = false;

      });

      ajaxReq.addEventListener('error', function () {
        showMsg("Other error");
        openButton.disabled = false;
      });

      ajaxReq.open('GET', BASE_URL + docId, true);
      ajaxReq.send();
      
    }
  );

})(window);
