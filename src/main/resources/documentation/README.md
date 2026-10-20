# Společná dokumentace

Každý `.properties` soubor představuje jednu kapitolu společnou pro všechny úrovně.
Hra vyhledává soubory automaticky bez indexu, ve vývoji přímo v této složce a v
zabalené hře uvnitř JAR. Seznam obnoví opětovné otevření záložky Dokumentace nebo F4.

```properties
id=09-moje-kapitola
title=Moje kapitola
description=Krátký popis v seznamu.
order=90
content=# Úvod\n\n\
Text kapitoly. Příklad volání: `move(Direction.RIGHT);`
```

`id` musí být jedinečné (písmena, číslice, pomlčky a podtržítka, nejvýše 64 znaků).
Bez id se použije název souboru. `title` je povinný jednořádkový název do 120 znaků,
`description` volitelný popis do 500 znaků, `order` celé číslo pro pořadí (výchozí 0).
Při shodném pořadí rozhoduje id. `content` obsahuje text kapitoly do 64 000 znaků;
prázdný obsah zobrazí informaci, že bude doplněn. Limit souboru je 64 kB v UTF-8.

Text používá stejný formát jako zadání: `# ` označuje nadpis, jednotlivé zpětné
apostrofy krátký kód ve větě a tři zpětné apostrofy blok kódu. `\n` vloží nový řádek,
zpětné lomítko na konci řádku pokračuje v hodnotě a `\ ` zachová počáteční mezeru.

Záložka Dokumentace nabízí seznam kapitol. Kliknutí nebo Enter otevře kapitolu
pouze v panelu mapy, objednávky a zásob. Vpravo zůstává seznam kapitol. V malém
okně přepíná F2 seznam a otevřenou kapitolu. Čtení kapitoly pozastaví program.
Kolečko posouvá panel pod kurzorem; šipky a Page Up / Page Down posouvají kapitolu.
Escape / Seznam kapitol vrátí seznam, F1 / Zavřít vrátí herní zobrazení.
