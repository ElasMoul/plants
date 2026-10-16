# Host routing shared by frontend/nginx.conf and deploy/dev-delivery/nginx.dev-delivery.conf (PLA-73).
# Included inside a `server {}` block. Five answers, in this order of precedence:
#   1. non-preferred host      -> 301 to the preferred host (host-redirect.conf.template)
#   2. prerendered public pages (/en/, /fr/)
#   3. the client-only app shell, for the routes the Angular router knows (CLIENT_ROUTE_PREFIXES in
#      src/app/client-routes.ts; client-routes.spec.ts fails when this list drifts from it)
#   4. real files (assets, chunks, robots.txt, sitemap.xml) - and 404 when the file does not exist
#   5. everything else -> 404 with the static 404.html, never the shell
# Locations that proxy (/api/, /photos/, ...) stay in the including file.

if ($pla_redirect_to_preferred) {
    return 301 $pla_redirect_scheme://$pla_preferred_host$request_uri;
}

absolute_redirect off;
error_page 404 /404.html;

location = /404.html {
    internal;
}

location = /en { return 301 /en/; }
location = /fr { return 301 /fr/; }

location = /en/ { try_files /en/index.html =404; }
location = /fr/ { try_files /fr/index.html =404; }

# The root keeps its current behaviour (landing for visitors, redirect for signed-in users).
location = / {
    add_header Cache-Control "no-cache, no-store, must-revalidate";
    try_files /index.csr.html =404;
}

# Crawler files: the file if it exists, otherwise 404 - never the shell or login page.
location = /robots.txt { try_files $uri =404; }
location = /sitemap.xml { try_files $uri =404; }

location ~ ^/(admin|chat|dashboard|garden|home|identify|login|plans|plants|preferences|register|reminders|treatment|treatment-plans|voice-test)(/|$) {
    add_header Cache-Control "no-cache, no-store, must-revalidate";
    try_files /index.csr.html =404;
}

location / {
    try_files $uri =404;
}
