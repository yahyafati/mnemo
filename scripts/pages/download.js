// The pages work without this script: their button leads to every download. With it, each button
// downloads the build for the visitor's own system, and the current version and date appear.
// Nothing else is fetched or stored.
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

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text) node.appendChild(document.createTextNode(text));
    return node;
  }

  function link(href, text) {
    var a = el("a", null, text);
    a.href = href;
    return a;
  }

  var list = document.getElementById("files");
  if (!list) return;
  var files = {};
  Array.prototype.forEach.call(list.querySelectorAll("a[data-os]"), function (a) {
    files[a.getAttribute("data-os")] = a;
  });
  var system = systemOf(navigator.userAgent, navigator.platform);
  var gets = document.querySelectorAll("[data-get]");

  Array.prototype.forEach.call(gets, function (get) {
    var help = get.getAttribute("data-help") || "";
    var button = get.querySelector(".button");
    var after = button;
    function add(node) {
      after.parentNode.insertBefore(node, after.nextSibling);
      after = node;
    }

    if (system === "ios") {
      button.href = help + "#all-files";
      button.textContent = "See all downloads";
      add(el("p", "small", "Mnemo is not available for iPhone or iPad. It runs on Android phones and on computers."));
      return;
    }
    var file = files[system];
    if (!file) return;
    button.href = file.href;
    button.textContent = "Download for " + file.getAttribute("data-label");

    var note = file.getAttribute("data-note");
    if (note) {
      var p = el("p", "small", note + " ");
      p.appendChild(link(help + "#" + file.getAttribute("data-section"), "What to click"));
      add(p);
    }
    // A browser says "Intel" on every Mac, so the Apple Silicon button gets the Intel build as its alternative.
    var other = files[system + "-intel"];
    if (other) {
      var alt = el("p", "small", "Mac with an Intel processor? ");
      alt.appendChild(link(other.href, "Get this version instead"));
      add(alt);
    }
    Array.prototype.forEach.call(get.querySelectorAll("[data-other]"), function (span) {
      span.appendChild(document.createTextNode(" · "));
      span.appendChild(link(help + "#all-files", "Other systems"));
    });
  });

  var releases = document.querySelectorAll("[data-release]");
  var none = document.querySelectorAll("[data-none]");
  if (!window.fetch) return;
  fetch(list.getAttribute("data-api"), { headers: { Accept: "application/vnd.github+json" } })
    .then(function (response) {
      if (response.status === 404) {
        // Nothing published yet: do not point at downloads that do not exist.
        Array.prototype.forEach.call(gets, function (get) { get.hidden = true; });
        Array.prototype.forEach.call(releases, function (line) { line.hidden = true; });
        Array.prototype.forEach.call(none, function (notice) { notice.hidden = false; });
        return null;
      }
      return response.ok ? response.json() : null;
    })
    .then(function (release) {
      if (!release || !release.tag_name) return;
      var version = "Version " + release.tag_name.replace(/^v/, "");
      var text = version;
      if (release.published_at) {
        var when = new Date(release.published_at);
        if (!isNaN(when)) {
          text += ", released " + when.toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" });
        }
      }
      Array.prototype.forEach.call(releases, function (line) {
        if (line.hasAttribute("data-short")) {
          line.textContent = " · " + version;
          return;
        }
        line.textContent = text + " · ";
        line.appendChild(link(release.html_url || list.getAttribute("data-fallback"), "What's new"));
      });
    })
    .catch(function () { /* offline or rate-limited: the static line stays */ });
})();
