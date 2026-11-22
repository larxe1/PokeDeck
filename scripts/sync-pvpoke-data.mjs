import { execFile } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, "..");
const assetsDir = path.join(projectRoot, "app", "src", "main", "assets");
const spritesDir = path.join(assetsDir, "sprites");

const PVPOKE_URL =
  "https://raw.githubusercontent.com/pvpoke/pvpoke/master/src/data/gamemaster/pokemon.json";
const SCRAPEDDUCK_RAIDS_URL =
  "https://raw.githubusercontent.com/bigfoott/ScrapedDuck/data/raids.min.json";
const POKEBATTLER_POKEMON_URL = "https://fight.pokebattler.com/pokemon";
const POKEBATTLER_MOVES_URL = "https://fight.pokebattler.com/moves";
const POKESPRITE_ZIP_URL =
  "https://github.com/msikma/pokesprite/archive/refs/heads/master.zip";
const POKEAPI_SPRITE_BASE =
  "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon";

const forceSprites = process.argv.includes("--force-sprites");

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to fetch ${url}: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

async function fetchBuffer(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} ${response.statusText} for ${url}`);
  }
  const arrayBuffer = await response.arrayBuffer();
  return Buffer.from(arrayBuffer);
}

function normalizePokemonEntry(entry) {
  const tags = Array.isArray(entry.tags) ? entry.tags : [];
  const speciesId = String(entry.speciesId ?? "").trim();
  if (!speciesId) return null;

  // Exclude shadow-only duplicate entries; runtime matching strips "Shadow" dynamically
  if (speciesId.endsWith("_shadow") || tags.includes("shadow")) {
    return null;
  }

  const types = Array.isArray(entry.types)
    ? entry.types
        .map((t) => (typeof t === "string" ? t.trim().toLowerCase() : ""))
        .filter((t) => t && t !== "none")
    : [];

  if (types.length === 0) {
    return null;
  }

  return {
    speciesId,
    speciesName: String(entry.speciesName ?? speciesId),
    dex: Number(entry.dex ?? 0),
    types,
    tags,
    _rawBaseStats: entry.baseStats ?? null,
    _rawFastMoves: Array.isArray(entry.fastMoves) ? entry.fastMoves : [],
    _rawChargedMoves: Array.isArray(entry.chargedMoves) ? entry.chargedMoves : [],
  };
}

function formatMoveDisplayName(moveId) {
  return moveId
    .replace(/_FAST$/, "")
    .split("_")
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(" ");
}

function normalizeMovesList(rawMovesJson) {
  const arr = Array.isArray(rawMovesJson?.move) ? rawMovesJson.move : [];
  const moves = [];
  for (const m of arr) {
    const moveId = String(m.moveId ?? "").trim();
    if (!moveId) continue;
    const rawType = String(m.type ?? "").trim();
    const type = rawType.replace(/^POKEMON_TYPE_/i, "").toLowerCase();
    if (!type || type === "none") continue;
    const durationMs = Number(m.durationMs ?? 0);
    if (durationMs <= 0) continue;
    const isFast = moveId.endsWith("_FAST") || Number(m.energyDelta ?? 0) > 0;
    moves.push({
      moveId,
      displayName: formatMoveDisplayName(moveId),
      type,
      power: Number(m.power ?? 0),
      durationMs,
      energyDelta: Number(m.energyDelta ?? 0),
      isFast,
    });
  }
  return moves;
}

function buildPokebattlerIndex(rawPbJson) {
  const arr = Array.isArray(rawPbJson?.pokemon) ? rawPbJson.pokemon : [];
  const map = new Map();
  for (const p of arr) {
    if (p?.pokemonId) {
      map.set(String(p.pokemonId).toUpperCase(), p);
    }
  }
  return map;
}

function candidatePokebattlerIds(speciesId) {
  const upper = speciesId.toUpperCase();
  const list = [
    upper,
    `${upper}_FORM`,
    upper.replace(/_ALOLAN\b/g, "_ALOLA_FORM"),
    upper.replace(/_GALARIAN\b/g, "_GALAR_FORM"),
    upper.replace(/_HISUIAN\b/g, "_HISUI_FORM"),
    upper.replace(/_PALDEAN\b/g, "_PALDEA_FORM"),
    upper.replace(/_ALOLAN\b/g, "_ALOLA"),
    upper.replace(/_GALARIAN\b/g, "_GALAR"),
    upper.replace(/_HISUIAN\b/g, "_HISUI"),
    upper.replace(/_PALDEAN\b/g, "_PALDEA"),
  ];
  // Common PvPoke -> Pokebattler form suffixes
  const baseFirst = upper.split("_")[0];
  if (baseFirst) {
    list.push(baseFirst);
  }
  return [...new Set(list)];
}

function enrichWithPokebattler(compactPokemon, pbMap, moveIdSet) {
  let pbMatched = 0;
  for (const entry of compactPokemon) {
    const candidates = candidatePokebattlerIds(entry.speciesId);
    let pb = null;
    for (const cand of candidates) {
      if (pbMap.has(cand)) {
        pb = pbMap.get(cand);
        break;
      }
    }

    const pbAtk = Number(pb?.stats?.baseAttack ?? 0);
    const pbDef = Number(pb?.stats?.baseDefense ?? 0);
    const pbSta = Number(pb?.stats?.baseStamina ?? 0);
    const pvAtk = Number(entry._rawBaseStats?.atk ?? 0);
    const pvDef = Number(entry._rawBaseStats?.def ?? 0);
    const pvSta = Number(entry._rawBaseStats?.hp ?? 0);

    entry.pokebattlerId = pb?.pokemonId ?? candidates[0];
    entry.baseAtk = pbAtk > 0 ? pbAtk : pvAtk;
    entry.baseDef = pbDef > 0 ? pbDef : pvDef;
    entry.baseSta = pbSta > 0 ? pbSta : pvSta;

    const pbQuick = Array.isArray(pb?.quickMoves)
      ? pb.quickMoves.filter((id) => moveIdSet.has(id))
      : [];
    const pbCin = Array.isArray(pb?.cinematicMoves)
      ? pb.cinematicMoves.filter((id) => moveIdSet.has(id))
      : [];

    const pvQuick = entry._rawFastMoves
      .map((m) => {
        const up = String(m).toUpperCase();
        return moveIdSet.has(`${up}_FAST`) ? `${up}_FAST` : up;
      })
      .filter((id) => moveIdSet.has(id));
    const pvCin = entry._rawChargedMoves
      .map((m) => String(m).toUpperCase())
      .filter((id) => moveIdSet.has(id));

    entry.quickMoves = pbQuick.length > 0 ? [...new Set(pbQuick)] : [...new Set(pvQuick)];
    entry.cinematicMoves = pbCin.length > 0 ? [...new Set(pbCin)] : [...new Set(pvCin)];

    if (pb) pbMatched++;

    delete entry._rawBaseStats;
    delete entry._rawFastMoves;
    delete entry._rawChargedMoves;
  }
  console.log(`Enriched ${compactPokemon.length} species (${pbMatched} direct Pokébattler matches).`);
}

function normalizeRaidEntry(entry) {
  return {
    name: entry.name ?? "",
    tier: entry.tier ?? "",
    canBeShiny: Boolean(entry.canBeShiny),
    types: Array.isArray(entry.types)
      ? entry.types.map((t) => ({ name: t?.name ?? "" })).filter((t) => t.name)
      : [],
    combatPower: entry.combatPower
      ? {
          normal: entry.combatPower.normal
            ? {
                min: entry.combatPower.normal.min ?? null,
                max: entry.combatPower.normal.max ?? null,
              }
            : null,
          boosted: entry.combatPower.boosted
            ? {
                min: entry.combatPower.boosted.min ?? null,
                max: entry.combatPower.boosted.max ?? null,
              }
            : null,
        }
      : null,
  };
}

async function extractZip(zipPath, destDir) {
  try {
    await execFileAsync("tar", ["-xf", zipPath, "-C", destDir], {
      maxBuffer: 20 * 1024 * 1024,
    });
    return;
  } catch (tarErr) {
    if (process.platform === "win32") {
      await execFileAsync(
        "powershell",
        [
          "-NoProfile",
          "-Command",
          `Expand-Archive -LiteralPath '${zipPath.replace(/'/g, "''")}' -DestinationPath '${destDir.replace(/'/g, "''")}' -Force`,
        ],
        { maxBuffer: 20 * 1024 * 1024 }
      );
      return;
    }
    throw tarErr;
  }
}

async function loadPokespriteIndex(extractedDir) {
  const topEntries = await fs.readdir(extractedDir, { withFileTypes: true });
  const repoDir = topEntries.find((e) => e.isDirectory())?.name;
  if (!repoDir) {
    throw new Error(`No extracted pokesprite root folder found in ${extractedDir}`);
  }
  const regularDir = path.join(extractedDir, repoDir, "pokemon-gen8", "regular");
  const index = new Map();

  async function scanDir(dir, prefix = "") {
    const entries = await fs.readdir(dir, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isFile() && entry.name.endsWith(".png")) {
        const slug = entry.name.slice(0, -4).toLowerCase();
        if (!prefix || !index.has(slug)) {
          index.set(prefix ? `${prefix}/${slug}` : slug, fullPath);
        }
      } else if (entry.isDirectory() && entry.name === "female") {
        await scanDir(fullPath, "female");
      }
    }
  }

  await scanDir(regularDir);
  return index;
}

const EXACT_SLUG_OVERRIDES = {
  nidoran_female: "nidoran-f",
  nidoran_male: "nidoran-m",
  mr_mime: "mr-mime",
  mr_mime_galarian: "mr-mime-galar",
  mr_rime: "mr-rime",
  mime_jr: "mime-jr",
  ho_oh: "ho-oh",
  porygon_z: "porygon-z",
  type_null: "type-null",
  jangmo_o: "jangmo-o",
  hakamo_o: "hakamo-o",
  kommo_o: "kommo-o",
  tapu_koko: "tapu-koko",
  tapu_lele: "tapu-lele",
  tapu_bulu: "tapu-bulu",
  tapu_fini: "tapu-fini",
  farfetchd: "farfetchd",
  farfetchd_galarian: "farfetchd-galar",
  sirfetchd: "sirfetchd",
  flabebe: "flabebe",
  zacian_crowned_sword: "zacian-crowned",
  zacian_hero: "zacian",
  zamazenta_crowned_shield: "zamazenta-crowned",
  zamazenta_hero: "zamazenta",
  calyrex_ice_rider: "calyrex-ice-rider",
  calyrex_shadow_rider: "calyrex-shadow-rider",
  necrozma_dusk_mane: "necrozma-dusk-mane",
  necrozma_dawn_wings: "necrozma-dawn-wings",
  necrozma_ultra: "necrozma-ultra",
  zygarde_10: "zygarde-10",
  zygarde_complete: "zygarde-complete",
  hoopa_confined: "hoopa",
  hoopa_unbound: "hoopa-unbound",
  meloetta_aria: "meloetta",
  meloetta_pirouette: "meloetta-pirouette",
  giratina_altered: "giratina",
  giratina_origin: "giratina-origin",
  shaymin_land: "shaymin",
  shaymin_sky: "shaymin-sky",
  deoxys_normal: "deoxys",
  deoxys_attack: "deoxys-attack",
  deoxys_defense: "deoxys-defense",
  deoxys_speed: "deoxys-speed",
  wormadam_plant: "wormadam",
  wormadam_sandy: "wormadam-sandy",
  wormadam_trash: "wormadam-trash",
  cherrim_overcast: "cherrim",
  cherrim_sunny: "cherrim-sunshine",
  darmanitan_standard: "darmanitan",
  darmanitan_zen: "darmanitan-zen",
  darmanitan_galarian_standard: "darmanitan-galar",
  darmanitan_galarian_zen: "darmanitan-galar-zen",
  tornadus_incarnate: "tornadus",
  tornadus_therian: "tornadus-therian",
  thundurus_incarnate: "thundurus",
  thundurus_therian: "thundurus-therian",
  landorus_incarnate: "landorus",
  landorus_therian: "landorus-therian",
  keldeo_ordinary: "keldeo",
  keldeo_resolute: "keldeo-resolute",
  aegislash_shield: "aegislash",
  aegislash_blade: "aegislash-blade",
  lycanroc_midday: "lycanroc",
  lycanroc_midnight: "lycanroc-midnight",
  lycanroc_dusk: "lycanroc-dusk",
  wishiwashi_solo: "wishiwashi",
  wishiwashi_school: "wishiwashi-school",
  oricorio_baile: "oricorio",
  oricorio_pom_pom: "oricorio-pom-pom",
  oricorio_pau: "oricorio-pau",
  oricorio_sensu: "oricorio-sensu",
  toxtricity_amped: "toxtricity",
  toxtricity_low_key: "toxtricity-low-key",
  eiscue_ice: "eiscue",
  eiscue_noice: "eiscue-noice",
  morpeko_full_belly: "morpeko",
  morpeko_hangry: "morpeko-hangry",
  urshifu_single_strike: "urshifu",
  urshifu_rapid_strike: "urshifu-rapid-strike",
  indeedee_male: "indeedee",
  indeedee_female: "female/indeedee",
  meowstic_male: "meowstic",
  meowstic_female: "female/meowstic",
  pyroar_female: "female/pyroar",
  frillish_female: "female/frillish",
  jellicent_female: "female/jellicent",
  unfezant_female: "female/unfezant",
  basculegion_female: "female/basculegion",
  oinkologne_female: "female/oinkologne",
  basculin_red_striped: "basculin",
  basculin_blue_striped: "basculin-blue-striped",
  basculin_white_striped: "basculin-white-striped",
  castform_sunny: "castform-sunny",
  castform_rainy: "castform-rainy",
  castform_snowy: "castform-snowy",
  kyurem_black: "kyurem-black",
  kyurem_white: "kyurem-white",
  kyogre_primal: "kyogre-primal",
  groudon_primal: "groudon-primal",
};

function baseSlugFromSpeciesName(speciesName) {
  const base = speciesName.replace(/\s*\([^)]*\)\s*$/, "").trim();
  return base
    .toLowerCase()
    .replace(/['’.]/g, "")
    .replace(/\s+/g, "-")
    .replace(/é/g, "e")
    .replace(/♀/g, "-f")
    .replace(/♂/g, "-m");
}

function candidatePokespriteSlugs(entry) {
  const id = entry.speciesId.toLowerCase();
  const candidates = [];
  if (EXACT_SLUG_OVERRIDES[id]) {
    candidates.push(EXACT_SLUG_OVERRIDES[id]);
  }
  const transformed = id
    .replace(/_alolan\b/g, "-alola")
    .replace(/_galarian\b/g, "-galar")
    .replace(/_hisuian\b/g, "-hisui")
    .replace(/_paldean\b/g, "-paldea")
    .replace(/_mega_x\b/g, "-mega-x")
    .replace(/_mega_y\b/g, "-mega-y")
    .replace(/_mega\b/g, "-mega")
    .replace(/_primal\b/g, "-primal")
    .replace(/_origin\b/g, "-origin")
    .replace(/_altered\b/g, "")
    .replace(/_dusk_mane\b/g, "-dusk-mane")
    .replace(/_dawn_wings\b/g, "-dawn-wings")
    .replace(/_crowned_sword\b/g, "-crowned")
    .replace(/_crowned_shield\b/g, "-crowned")
    .replace(/_therian\b/g, "-therian")
    .replace(/_incarnate\b/g, "")
    .replace(/_unbound\b/g, "-unbound")
    .replace(/_confined\b/g, "")
    .replace(/_aria\b/g, "")
    .replace(/_pirouette\b/g, "-pirouette")
    .replace(/_complete\b/g, "-complete")
    .replace(/_black\b/g, "-black")
    .replace(/_white\b/g, "-white")
    .replace(/_ice_rider\b/g, "-ice-rider")
    .replace(/_shadow_rider\b/g, "-shadow-rider")
    .replace(/_female\b/g, "-f")
    .replace(/_male\b/g, "-m")
    .replace(/_/g, "-");

  candidates.push(transformed);
  candidates.push(id.replace(/_/g, "-"));

  const baseFromName = baseSlugFromSpeciesName(entry.speciesName);
  if (baseFromName) candidates.push(baseFromName);
  const firstToken = id.split("_")[0];
  if (firstToken) candidates.push(firstToken);

  return [...new Set(candidates.filter(Boolean))];
}

async function runWithConcurrency(items, concurrency, worker) {
  const results = new Array(items.length);
  let nextIndex = 0;

  async function runNext() {
    while (true) {
      const current = nextIndex++;
      if (current >= items.length) return;
      results[current] = await worker(items[current], current);
    }
  }

  const runners = Array.from({ length: Math.min(concurrency, items.length) }, () => runNext());
  await Promise.all(runners);
  return results;
}

async function tryReuseExistingSprites(compactPokemon) {
  if (forceSprites) return false;
  try {
    const existingSprites = new Set(await fs.readdir(spritesDir));
    if (existingSprites.size < 1000) return false;
    const prevDbPath = path.join(assetsDir, "pokemon_db.json");
    const prevDb = JSON.parse(await fs.readFile(prevDbPath, "utf8"));
    const prevById = new Map(prevDb.map((p) => [p.speciesId, p.spriteFile]));
    let hits = 0;
    for (const entry of compactPokemon) {
      const sf = prevById.get(entry.speciesId);
      if (sf && existingSprites.has(sf)) {
        entry.spriteFile = sf;
        hits++;
      }
    }
    if (hits >= compactPokemon.length * 0.95) {
      console.log(`Reused ${hits}/${compactPokemon.length} bundled offline sprites (pass --force-sprites to re-fetch).`);
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

async function bundleSprites(compactPokemon) {
  if (await tryReuseExistingSprites(compactPokemon)) {
    return;
  }

  await fs.rm(spritesDir, { recursive: true, force: true });
  await fs.mkdir(spritesDir, { recursive: true });

  const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "pokesprite-"));
  const zipPath = path.join(tempRoot, "pokesprite.zip");
  const extractDir = path.join(tempRoot, "extracted");
  await fs.mkdir(extractDir, { recursive: true });

  try {
    console.log("Downloading pokesprite archive...");
    const zipBuffer = await fetchBuffer(POKESPRITE_ZIP_URL);
    await fs.writeFile(zipPath, zipBuffer);

    console.log("Extracting pokesprite archive...");
    await extractZip(zipPath, extractDir);

    const pokespriteIndex = await loadPokespriteIndex(extractDir);
    const copiedFiles = new Set();
    const missingForPokeApi = [];
    let pokespriteHits = 0;

    for (const entry of compactPokemon) {
      const candidates = candidatePokespriteSlugs(entry);
      let matchedKey = null;
      for (const candidate of candidates) {
        if (pokespriteIndex.has(candidate)) {
          matchedKey = candidate;
          break;
        }
      }

      if (matchedKey) {
        const safeOutName = matchedKey.replace(/\//g, "-") + ".png";
        if (!copiedFiles.has(safeOutName)) {
          await fs.copyFile(pokespriteIndex.get(matchedKey), path.join(spritesDir, safeOutName));
          copiedFiles.add(safeOutName);
        }
        entry.spriteFile = safeOutName;
        pokespriteHits++;
      } else {
        missingForPokeApi.push(entry);
      }
    }

    const uniqueDexes = [...new Set(missingForPokeApi.map((e) => e.dex).filter((d) => d > 0))];
    const downloadedDexFiles = new Map();
    await runWithConcurrency(uniqueDexes, 16, async (dex) => {
      const url = `${POKEAPI_SPRITE_BASE}/${dex}.png`;
      try {
        const pngBuffer = await fetchBuffer(url);
        if (pngBuffer.length > 0) {
          const filename = `dex_${dex}.png`;
          await fs.writeFile(path.join(spritesDir, filename), pngBuffer);
          downloadedDexFiles.set(dex, filename);
        }
      } catch (err) {
        console.warn(`Warning: failed PokeAPI sprite #${dex}: ${err.message}`);
      }
    });

    for (const entry of missingForPokeApi) {
      entry.spriteFile = downloadedDexFiles.get(entry.dex) ?? null;
    }
    console.log(`Sprite bundling complete (${pokespriteHits} pokesprite + PokeAPI fallback).`);
  } finally {
    await fs.rm(tempRoot, { recursive: true, force: true });
  }
}

async function main() {
  await fs.mkdir(assetsDir, { recursive: true });

  console.log("Fetching PvPoke, Pokébattler Pokémon/Moves, and ScrapedDuck Raids...");
  const [rawPokemon, rawPbPokemon, rawPbMoves, rawRaids] = await Promise.all([
    fetchJson(PVPOKE_URL),
    fetchJson(POKEBATTLER_POKEMON_URL),
    fetchJson(POKEBATTLER_MOVES_URL),
    fetchJson(SCRAPEDDUCK_RAIDS_URL),
  ]);

  if (!Array.isArray(rawPokemon)) {
    throw new Error("Expected PvPoke pokemon.json to be an array");
  }

  const moves = normalizeMovesList(rawPbMoves);
  const moveIdSet = new Set(moves.map((m) => m.moveId));
  const movesOutPath = path.join(assetsDir, "moves_db.json");
  await fs.writeFile(movesOutPath, JSON.stringify(moves) + "\n", "utf8");
  console.log(`Wrote ${moves.length} moves to ${movesOutPath}`);

  const compactPokemon = rawPokemon.map(normalizePokemonEntry).filter(Boolean);
  await bundleSprites(compactPokemon);

  const pbMap = buildPokebattlerIndex(rawPbPokemon);
  enrichWithPokebattler(compactPokemon, pbMap, moveIdSet);

  const pokemonOutPath = path.join(assetsDir, "pokemon_db.json");
  await fs.writeFile(pokemonOutPath, JSON.stringify(compactPokemon) + "\n", "utf8");
  console.log(`Wrote ${compactPokemon.length} species to ${pokemonOutPath}`);

  if (Array.isArray(rawRaids)) {
    const raidsFallback = rawRaids.map(normalizeRaidEntry);
    const raidsOutPath = path.join(assetsDir, "raids_fallback.json");
    await fs.writeFile(raidsOutPath, JSON.stringify(raidsFallback, null, 2) + "\n", "utf8");
    console.log(`Wrote ${raidsFallback.length} raids to ${raidsOutPath}`);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
