-- Business users open Excel: the example flows attach .xlsx instead of .csv.
UPDATE flow_definition SET definition_json = REPLACE(definition_json, '"fileName": "departman-kadrosu.csv"',
    '"format": "xlsx", "fileName": "departman-kadrosu.xlsx"') WHERE name = 'Haftalık departman kadrosu';
UPDATE flow_definition SET definition_json = REPLACE(definition_json, '"fileName": "egitim-uyumu.csv"',
    '"format": "xlsx", "fileName": "egitim-uyumu.xlsx"') WHERE name = 'Zorunlu eğitim uyumu';
