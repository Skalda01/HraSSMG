# Vlastní úrovně

Zkopíruj `03-workshop.properties.example` pod novým názvem s příponou `.properties`, nastav jedinečné `id` a uprav mapu.
Příklad s příponou `.example` se ve výběru úrovní nezobrazuje.
Soubor ulož jako UTF-8 do složky `levels` vedle pracovního adresáře hry.
Ve hře otevři **Úrovně / F3**, stiskni **Obnovit / F4**, vyber úroveň a potvrď
**Zapnout / Enter**. Šipky a kolečko posouvají výběr, Escape nabídku zavře.
Seznam se obnoví také při každém otevření nabídky úrovní.
Výběr má kapitoly **Robot** (skladové úrovně včetně bludiště a nekonečného režimu)
a **Podklady**. Po otevření Podkladů vyber podkapitolu **Pole** (režim `array`)
nebo **Algoritmy** (`bubblesort` a `binarysearch`), a potom konkrétní úroveň.
Kapitolu vyber kliknutím, šipkami vlevo/vpravo nebo Tabem. Šipky nahoru/dolů
vybírají úroveň uvnitř kapitoly. `isVisible=false` ji skryje i v tomto rozdělení.
Ze seznamu Pole nebo Algoritmy se tlačítkem Zpět, Esc nebo Backspace vrátíš
na výběr obou podkapitol.

Přibalené úrovně přidávej přímo do `src/main/resources/levels/` jako `.properties`.
Hra je vyhledává automaticky, bez `index.txt` a bez pevně daného názvu souboru.
Ve vývoji F4 načte i nově přidané, přejmenované nebo odstraněné zdrojové definice
bez překladu. V JAR se automaticky prohledají zabalené resources. První platná
přibalená úroveň podle `id` je počáteční; pořadí dalších úrovní rovněž určuje `id`.

```properties
id=04-my-level
name=Moje úroveň
description=Doruč jednu zásilku od každého typu.
width=8
height=6
start=1,4
dock=0,4
goal.orange=1
goal.purple=1
shelves=orange,5,1,2; purple,6,4,1
obstacles=3,0; 3,1; 3,2
```

- `id`: 1–64 ASCII písmen, číslic, pomlček nebo podtržítek, začíná písmenem či číslicí.
  Musí být jedinečné i vůči přibaleným úrovním; vynechané `id` se vezme z názvu souboru.
- `name`: název do 80 znaků. `description`: stručný popis do 2000 znaků.
- `isVisible`: `true` zobrazí úroveň ve výběru a zařadí ji do postupu na další level;
  `false` ji skryje. Chybějící klíč znamená `true`. Změna se načte při otevření
  výběru úrovní nebo přes F4. Již spuštěná úroveň zůstane zachovaná.
- `initialCode`: celý počáteční Java program, včetně importů, třídy `Main` a metody `main`.
  Prázdné `initialCode=` otevře úplně prázdný editor. Vynechaný klíč použije automatickou šablonu.
- `allowedKeywords`: volitelný seznam povolených příkazů API a řídicích konstrukcí,
  oddělených čárkami (lze také mezerami nebo středníky). Chybějící nebo prázdný
  seznam dovoluje vše. Například `allowedKeywords=move,pick,drop` nepovolí cykly,
  podmínky ani ostatní příkazy robota. Názvy rozlišují velká a malá písmena.
  API: `move`, `pick`, `drop`, `tileType`, `shelfType`,
  `cargoType`, `orderRemaining`, `robotCoordinatesX`, `robotCoordinatesY`.
  Konstrukce: `for` (i rozšířený for), `while`, `do`, `if`, `else`, `elseif`, `switch`, `break`,
  `continue`, `return`, `try`, `throw`, `assert`, `synchronized`.
  `else` povoluje záložní větev podmínky. Pro větev `else if` povolte `if` a `elseif`,
  případně dvojici `if` a `else`. Našeptávač převede `elseif` na Java zápis `else if`;
  v samotném Java kódu se píše se dvěma slovy. `switch` zahrnuje `case` a `default`, `try` zahrnuje
  `catch` a `finally`; do-while potřebuje `do` i `while`.
  Zápis `brake` se normalizuje na `break`.
  Kontroluje se i vložený nebo uložený kód, pomocné metody, kvalifikovaná volání
  `Robot.metoda()` a reference `Robot::metoda`. Komentáře a řetězce se ignorují.
  Kostra Java třídy, deklarace proměnných, importy, enumy a ostatní Java API
  nejsou tímto seznamem omezené. Po změně pravidel načti a aktivuj definici přes
  F3 → F4 → Zapnout. Také `initialCode` musí odpovídat nastaveným pravidlům.
- `assignment`: celé znění záložky Zadání, nahrazuje automaticky sestavený text.
- `requiredKeywords`: volitelný seznam povinných konstrukcí nebo příkazů Robot API.
  Používá stejné názvy jako `allowedKeywords`; povinný příkaz musí být současně povolený.
  Kontroluje se Java syntaxe a skutečné volání API, komentáře ani řetězce nestačí.
  Například `requiredKeywords=switch` vyžaduje konstrukci switch v programu.
- `goal.position`: cílové políčko ve formátu `x,y`, například `goal.position=2,4`.
  Na mapě se označí jako CÍL. Úroveň se splní po úspěšném ukončení programu
  s robotem na tomto políčku. Cíl musí být dosažitelný a lze ho použít pouze
  v kampani; úroveň nemusí mít žádné police ani dock.
  Ukázka: `src/main/resources/levels/05-shift.properties`.
- `expectedOutput`: přesný očekávaný standardní výstup hráčova programu. Například
  `expectedOutput=PARTS` ověří `System.out.println(cargoType());`. Zkratka `sout`
  v našeptávači vkládá běžné `System.out.println(...)`; nejde o zvláštní příkaz API.
  Více řádků zapisuj pomocí `\n`. Rozlišují se mezery, pořadí řádků a velikost písmen;
  ignoruje se jeden konečný znak nového řádku a rozdíl mezi CRLF a LF.
  Chybějící položka výstup nekontroluje; prázdná položka požaduje prázdný výstup.
  Kontrola čeká na úspěšné ukončení programu, takže odmítne i nadbytečné výpisy.
  Záložka Konzole zobrazuje skutečný i požadovaný výstup.
- `goal.visitAllTiles=true`: navštívit a vypsat každé průchozí políčko právě jednou.
  Každý řádek konzole musí mít přesný tvar `tile(x,y)` a odpovídat aktuální poloze
  robota. Pořadí políček je libovolné; nadbytečné řádky a duplicity jsou chyba.
  Level se vyhodnotí po úspěšném ukončení programu. Při nulových cílech doručení
  může chybět `dock` a `shelves` může být prázdné. Všechna průchozí políčka musí
  být dosažitelná.
- `goal.visitPerimeter=true`: projít celý vnější obvod, na každém jeho políčku
  právě jednou vypsat `tile(x,y)` a vrátit se na start. Po návratu se místo
  duplicitních souřadnic vypíše `HOTOVO`. Po tomto posledním řádku musí program
  úspěšně skončit. Start musí ležet na obvodu, obvod musí být bez překážek a robot
  nesmí procházet vnitřkem mapy. Nelze kombinovat s `goal.visitAllTiles`.
  Ukázka: `src/main/resources/levels/03-tiles.properties`.
- `goal.cargo`: typ balíku, který musí robot při splnění úkolu nést (`orange`,
  `purple`, `tools`, `materials`). Tento cíl lze kombinovat s doručením i výpisem.
  Pokud má robot pouze vyzvednout a vypsat balík, nastav všechny doručovací cíle na 0,
  například `goal.orange=0`, `goal.cargo=orange`, `expectedOutput=PARTS`.
  Ukázka je v `../src/main/resources/levels/0300b-output.properties`.
- `documentation`: starší položka se ignoruje. Dokumentace je pro všechny úrovně
  zatím prázdná a z konfigurace se nenačítá.
  `initialCode` a `assignment` mohou mít nejvýše 30 000 znaků. Chybějící nebo prázdná
  položka použije dosavadní výchozí obsah. Soubor vlastní úrovně má limit 64 kB.
- `mode`: `campaign` (výchozí) nebo `endless` pro nekonečné zakázky.
  Algoritmické režimy jsou `bubblesort`, `binarysearch` a `array`.
  Režim `array` zobrazuje int[] jako buňky s indexy a animuje čtení i zápis.
  `array` určuje 1–32 výchozích hodnot typu int (včetně nuly a záporných čísel),
  `goal.array` požadované výsledné hodnoty se stejným počtem prvků.
  Lze současně nastavit `expectedOutput`. Požaduje se úspěšný konec programu;
  řazení se v tomto režimu nekontroluje. Série `06-array-01-fill.properties`
  až `06-array-09-second-largest.properties` v resources tvoří devět úkolů s polem.
  Režim `arraysandbox` používá vlastní int[] z hráčova kódu bez `array`, `goal.array`,
  `expectedOutput`, `allowedKeywords` a `requiredKeywords`. Délka může být i nulová
  a není omezena na 32 prvků. Canvas automaticky přepíná používaná pole a stránkuje
  velká pole. Ukázka: `06-array-10-sandbox.properties`.
- `width`, `height`: 2–25 sloupců a 2–25 řádků.
- `start`, `dock`: souřadnice `x,y`, od nuly vlevo nahoře. Mohou být stejné.
  `dock` lze vynechat u úrovně bez doručování balíků; na mapě se pak nezobrazuje.
- `inbound`: souřadnice příjmu, povinné pro `endless`, v kampani se nepoužívají.
- `goal.orange`, `goal.purple`, `goal.tools`, `goal.materials`: požadované počty 0–99,
  alespoň jeden musí být kladný; vynechané hodnoty jsou nula.
- `shelves`: regály oddělené `;`, každý má formát `typ,x,y,zásoba`.
  Typ je `orange` (součástky), `purple` (elektronika), `tools` (nástroje) nebo `materials` (materiál).
  Počáteční zásoba je 1–999 v kampani a 0–999 ve skladu. Kapacita nekonečného skladu nemá limit.
  Každý regál má vlastní zásobu.
- Panel objednávky přebírá požadované typy a počty z `goal.*`. V kampani se typy
  s nulovým cílem nezobrazují. Nekonečný sklad zobrazuje všechny čtyři typy,
  protože požadavky dalších zakázek se mění.
- Spodní menu skladu vytváří položky podle `shelves`, v pořadí prvního výskytu
  každého typu. Sčítá aktuální zásoby všech polic stejného typu. I po vyčerpání
  zásoby zůstane položka vidět s nulou. Typy bez police se nezobrazují.
- `obstacles`: pole oddělená `;`, každé ve formátu `x,y`. Pro prázdnou mapu nech hodnotu prázdnou.

Kompletní příklad kódu a zadání je v
[`01-shift.properties`](../src/main/resources/levels/01-shift.properties).
Používá standardní zápis Java Properties: `\n` vloží nový řádek a `\` na konci
fyzického řádku pokračuje v hodnotě na dalším řádku. Odsazení zachovej zápisem
`\ ` před první mezerou. Zpětné lomítko uvnitř hodnoty zapisuj jako `\\`
(například Java řetězec s `\n` vyžaduje v properties `\\n`).
V zadání označuje `# ` na začátku textového řádku nadpis;
prázdné řádky oddělují odstavce. Celý text včetně nadpisů určuje tato hodnota.

Ukázky kódu v zadání se zobrazují na tmavém pozadí, pevným písmem a se zvýrazněním
Java syntaxe. Víceřádkový blok uzavři mezi řádky s třemi zpětnými apostrofy
(```` ```java ```` a ```` ``` ````). Krátký kód ve větě označ například
`` `int i = 0` ``. Samostatné příkazy a cykly i volání metod v běžných větách se
rozpoznávají automaticky. Pro jednoznačné formátování použij značky.
Bloky zachovávají odsazení; dlouhé řádky se vizuálně zalomí podle šířky panelu.
V properties stále používej `\n` a `\ ` pro zachování mezer na začátku řádku.

```properties
initialCode=import static robot.api.Robot.*;\n\
public class Main {\n\
\    public static void main(String[] args) {\n\
\        move(Direction.RIGHT);\n\
\    }\n\
}\n
assignment=# Moje zadání\n\n\
Doruč jednu zásilku od každého typu.
```

Uložený rozepsaný program má přednost před `initialCode`. Výchozí kód obnoví tlačítko
**Reset kódu**. Obnovení jde vrátit
přes **Ctrl+Z** a zastaví běžící program; stav skladu zůstává zachovaný.
Text zadání se načítá vždy
z aktivované definice; jeho změna nepřepíše hráčův program. Když upravíš mapu nebo
pravidla, uprav také vlastní zadání, aby popis odpovídal úrovni.
Změny zdrojových definic při spuštění z projektu načte F4; aktivuje je tlačítko
Zapnout. F4 neupravuje zabalené resources v JAR, tam je potřeba nové sestavení.

Překážky nesmějí zasahovat do startu, expedice a regálů. Regály se nesmějí překrývat
ani stát na expedici. Start na regálu je povolen. Expedice a dostatečné množství zásilek
musí být dostupné ze startu. Pohyb hráč programuje jednotlivými příkazy `move(Direction)`.
Případné obcházení stěn napiš do hráčova Java programu.

V režimu `endless` tvoří čtyři cíle požadované počty první zakázky. Po splnění se
vymažou jen průběžné počty doručení a vygeneruje se jiná zakázka s 0–4 kusy od každého
typu a alespoň jedním balíkem celkem. Police ani zásoby se nemění. Cíle aktuální
zakázky jsou v panelu vlevo od mapy, skladové zásoby ve spodní liště. `orderRemaining(ShelfType...)` vrátí počet kusů,
které u daného typu ještě zbývá odeslat; dotazy po splnění už čtou novou zakázku.
Je nutná jedna dosažitelná police pro každý ze čtyř typů, dosažitelný příjem a expedice.
Příjem se nesmí překrývat s polici, expedicí nebo překážkou. Po vyzvednutí vytvoří další
balík; všechny čtyři typy se pravidelně střídají.

`tileType()` rozliší FLOOR, SHELF, DOCK a INBOUND. `shelfType()` určí sortiment police,
`cargoType()` určí nesený balík. `pick()` na příjmu přebere jeden balík, `drop()` jej
uloží pouze do police stejného typu. Před odesláním na dock musí balík projít policí.
Robot stále unese jen jeden balík. Na mapě s překážkami hráč sám zajišťuje průchodnou cestu.
V přibaleném skladu je dock `[0,1]`, příjem `[1,0]` a police na spodním řádku v X = 1,3,5,7.
Vzor speciální úrovně je `../src/main/resources/levels/0994-infinite-orders.properties`.

Vadná definice se nezapne; nabídka úrovní vypíše název souboru a důvod chyby.
Obnovení katalogu nemění běžící mapu, novou definici použije až tlačítko Zapnout.
Při výběru úrovně se přehrávání pozastaví. Aktivace vyresetuje mapu a načte její vlastní program.
Program první úrovně zůstává v `robot-program.java.txt`, ostatní se ukládají
do `programs/<id>.java.txt`. Při přepnutí se rozepsaný program automaticky uloží.
