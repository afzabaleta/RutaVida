import { cp, mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const source = resolve("frontend");
const output = resolve("dist");
const rawApiUrl = (process.env.RUTAVIDA_API_BASE_URL || "").trim();

function apiOrigin(value) {
  if (!value) return "";
  const url = new URL(value);
  if (url.protocol !== "https:" || url.pathname !== "/" || url.search || url.hash || url.username || url.password) {
    throw new Error("RUTAVIDA_API_BASE_URL debe ser un origen HTTPS sin ruta, por ejemplo https://rutavida-api.onrender.com");
  }
  return url.origin;
}

const apiUrl = apiOrigin(rawApiUrl);
const websocketOrigin = apiUrl ? apiUrl.replace(/^https:/, "wss:") : "";

async function htmlFiles(folder) {
  const entries = await readdir(folder, { withFileTypes: true });
  return (await Promise.all(entries.map(async (entry) => {
    const path = resolve(folder, entry.name);
    if (entry.isDirectory()) return htmlFiles(path);
    return entry.isFile() && entry.name.endsWith(".html") ? [path] : [];
  }))).flat();
}

await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });
await cp(source, output, { recursive: true });

for (const file of await htmlFiles(output)) {
  let html = await readFile(file, "utf8");
  html = html.replace("<head>", `<head>\n  <meta name="rutavida-api-base" content="${apiUrl}">`);
  if (apiUrl) {
    html = html.replace("connect-src 'self'", `connect-src 'self' ${apiUrl} ${websocketOrigin}`);
  }
  await writeFile(file, html, "utf8");
}

console.log(`Frontend preparado en dist (${apiUrl || "API same-origin/local"}).`);
