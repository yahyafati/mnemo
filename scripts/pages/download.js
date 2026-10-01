// The page works without this script: every download is in the table. With it, the visitor's own system
// gets a big button on top, and the current version and date appear. Nothing else is fetched or stored.
(function () {
  "use strict";

  function systemOf(ua, platform) {
    ua = ua || "";
    if (/Android/i.test(ua)) return "android";
    if (/iPhone|iPad|iPod/i.test(ua)) return "ios";
    if (/CrOS/i.test(ua)) return null; // a Chromebook: let them choose
    if (/Windows/i.test(ua)) return "windows";
    if (/Macintosh|Mac OS X/i.test(ua) || /^Mac/i.test(platform || "")) return "macos";
    if (/Fedora|SUSE|Red Hat|CentOS/i.test(ua)) return "rpm";
    if (/Ubuntu|Debian|Mint/i.test(ua)) return "deb";
    if (/Linux|X11/i.test(ua)) return "linux";
    return null;
  }

  var pick = document.getElementById("pick");
  var rows = {};
  Array.prototype.forEach.call(document.querySelectorAll("tr[data-os]"), function (row) {
    rows[row.getAttribute("data-os")] = row;
  });

  var system = systemOf(navigator.userAgent, navigator.platform);
  if (pick && system === "ios") {
    pick.textContent = "Mnemo is not available for iPhone or iPad. You can use it on a computer or an Android phone: all downloads are below.";
    pick.className = "notice";
    pick.hidden = false;
  } else if (pick && system && rows[system]) {
    var row = rows[system];
    var link = document.createElement("a");
    link.className = "button";
    link.href = row.querySelector("a").href;
    link.textContent = "Download for " + row.getAttribute("data-label");
    pick.appendChild(link);
    var note = row.getAttribute("data-note");
    if (note) {
      var small = document.createElement("p");
      small.className = "small";
      small.textContent = note;
      pick.appendChild(small);
    }
    var more = document.createElement("p");
    more.className = "small";
    more.innerHTML = 'Not your system? <a href="#all-files">Every file is below</a>, and <a href="#install">what to click when your system warns you</a>.';
    pick.appendChild(more);
    pick.hidden = false;
  }

  var status = document.getElementById("release-status");
  var none = document.getElementById("no-release");
  if (!status || !window.fetch) return;
  var api = status.getAttribute("data-api");
  fetch(api, { headers: { Accept: "application/vnd.github+json" } })
    .then(function (response) {
      if (response.status === 404) {
        // Nothing published yet: do not point at downloads that do not exist.
        status.hidden = true;
        if (pick) pick.hidden = true;
        if (none) none.hidden = false;
        return null;
      }
      return response.ok ? response.json() : null;
    })
    .then(function (release) {
      if (!release || !release.tag_name) return;
      var text = "Version " + release.tag_name.replace(/^v/, "");
      if (release.published_at) {
        var when = new Date(release.published_at);
        if (!isNaN(when)) {
          text += ", released " + when.toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" });
        }
      }
      status.textContent = text + " · ";
      var notes = document.createElement("a");
      notes.href = release.html_url || status.getAttribute("data-fallback");
      notes.textContent = "release notes";
      status.appendChild(notes);
    })
    .catch(function () { /* offline or rate-limited: the static line stays */ });
})();
