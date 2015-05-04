# Stupendously Simple Shitty Site Scraper & Snapshotter

Okay, so we all have one of those old dynamic websites kicking around.  You know the one, it was probably written using a combination of PHP, MySQL, bailing twine and idealistic-but-ultimately-misguided dreams.

But time has moved on.  The version of PHP it runs on doesn’t exist any more, the colo server costs more than you care to admit and the majority of traffic is simply bots looking for phpmyadmin exploits, or attempting to compromise your contact emailer form.

So the time has come to finally take down the site, decommissioning the database and erasing all of the sensitive account information and unhashed user passwords stored therein.

But: Gosh darn it!  You just can’t imagine the web being the same without your little baby.

What you really want to do is be able to make a completely static copy of your site that you can easily host on something without a brain, like AWS S3.  From there you can browse through your now defunct site and revel in the nostalgia of your drop shadows and pillow embossed logos, safe in the knowledge that there is nothing left to exploit and no server costs to pay.

So, the first thing you do is reach for everyone’s best friend: wget.  Unfortunately you find that it simply doesn’t quite do the right thing.  The resource paths appear to be screwy, and it just doesn’t play nice with things like pages on parent paths.

Enter: the **Stupendously Simple Shitty Site Scraper™**
<sub>* not actually trademaked</sub>

This nifty little web crawler will start at a given URL, then spider its way to local copy website glory, being careful to only stay within the confines of the given domains.  Each page is lovingly updated to ensure every src and href refers to a local copy of the retrieved resource.  When it has exhausted all links, or simply gone as deep into your site as anyone would dare you are left with a neat little copy of your site, ready for browsing.

Features include:
* Blazingly fast single-threaded operation! <sup>[1]</sup>
* Complete disregard for robots.txt! <sup>[2]</sup>
* Clojure! <sup>[3]</sup>
* Traverses and transforms both HTML and CSS resources!
* Latin1 support! <sup>[4]</sup>
* HTML <base> Element support
* Tolerant of single quoted, double quoted, or completely unquoted attributes!
* Astonishingly, it runs completely without judgement

<sup>[1]: okay, not fast, but anything more than 1 simultaneous request would take down the site anyway so it’s a feature.</sup>
<sup>[2]: seriously, don’t use this on other people’s interwebs.</sup>
<sup>[3]: because.</sup>
<sup>[5]: don’t pretend you don’t need it</sup>

## How does it work?

Well, you simply clone this repo, then run something like `lein run . --help` to get the following

    Stupendously Simple Shitty Site Scraper.

    Usage: program-name [options] URL

    Options:
      -m, --max-depth DEPTH  15                Maximum spidering recursion depth
      -d, --domains DOMAINS                    Comma separated list of valid domains
      -o, --output OUTPUT    resources/public  Local directory to save to
      -h, --help

Then something like the following should get you started: `lein run . http://example.com/index.html`

... and after a few seconds, your `resources/public` directory will look something like this:

    ./example.com
    ./example.com/index.html

Now all you have to do is take the contents of the public directory, and put it behind your favourite hipster webserver and voila!  Your work is presented in all of its wondrous static glory!

