/** Rules of the saved dataset's name (used in flows): `departman_kadrosu`. Same as the API's. */
export const DATASET_NAME_PATTERN = /^[a-z][a-z0-9_]{1,39}$/

/** A name suggestion from the label: "Departman kadrosu (aylık)" -> "departman_kadrosu_aylik". */
export function datasetNameFrom(label: string): string {
  const ascii = label.toLocaleLowerCase("tr-TR")
    .replace(/ı/g, "i").replace(/ğ/g, "g").replace(/ü/g, "u").replace(/ş/g, "s").replace(/ö/g, "o").replace(/ç/g, "c")
    .normalize("NFD").replace(/[̀-ͯ]/g, "")
  let name = ascii.replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "")
  if (!/^[a-z]/.test(name)) name = `veri_${name}`
  return name.slice(0, 40).replace(/_+$/, "")
}
