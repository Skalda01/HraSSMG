# WORKER // OS

Programovací hra v Javě 21 se Swing/Java2D a volitelným LWJGL / OpenGL 3.3.
Robot ve skladu doručuje součástky a elektroniku podle programu napsaného v editoru.

## Spuštění

Po `git clone` na Windows spusť **`spustit.cmd`**. Přeloží zdroje a otevře hru.
Použije přibalené Java knihovny z `lib/`, takže nepotřebuje Maven, internet,
instalaci závislostí ani rozbalování DLL. Aktualizace zdrojů se při dalším spuštění přeloží znovu.

Je potřeba již nainstalované a školou povolené **JDK 21 nebo novější** včetně `javac`,
protože hra překládá hráčův kód. Nastav `JAVA_HOME` na JDK nebo měj `java` a `javac` na PATH.
V IntelliJ stačí načíst Maven projekt, nastavit stejné JDK a spustit `org.example.Main`.
Výchozí renderer používá pouze Swing/Java2D z JDK, bez načítání `lwjgl.dll`, GLFW a STB.
Přibalený font, úrovně, editor, krokování i kompilace studentského programu zůstávají dostupné.

Hra vyžaduje zápis do klonu (`target/`, uložená řešení) a do dočasné složky pro
zdroje a `.class` studentských programů. Ty spouští pomocí `java.exe` ze stejného
povoleného JDK; žádný další EXE neinstaluje ani nerozbaluje. Jestli školní pravidla
zakazují i tento způsob spuštění Javy, samotný projekt povolení nahradit nemůže.

S Mavenem na PATH: `mvn compile exec:java`.

Původní OpenGL renderer je volitelný: v IntelliJ přidej VM parametr
`-Dhrassmg.renderer=opengl`. Tento režim používá Maven nativní závislosti Windows x64
a potřebuje povolené načítání DLL. Pro školní počítače ponech výchozí Java2D režim.

## První směna

- Doruč **3 součástky** a **2 elektroniky** na zelenou expedici `[0,3]`.
- Oranžové regály `[4,1]` a `[4,5]` mají každý vlastní zásobu čtyř součástek.
- Fialový regál `[6,3]` obsahuje elektroniku.
- Robot začíná na `[2,3]` a unese jednu zásilku. Překážky jsou na `[9,1]` a `[9,5]`.
- Souřadnice začínají vlevo nahoře na `[0,0]`; `move(Direction.TOP)` zmenšuje Y.

Při prvním spuštění obsahuje editor počáteční program vybrané úrovně.
Znovu ho obnovíš tlačítkem **Reset kódu**.

Tlačítko **Reset kódu** vedle ovládání programu obnoví původní program aktuálního
levelu z `initialCode`. Je dostupné i v malém okně. Obnovení zastaví běžící program,
stav skladu zachová a předchozí kód můžeš vrátit přes **Ctrl+Z**. Během překladu
je nutné počkat na jeho dokončení.

## Psaní programu

Level **Binary search** najdete přes **Úrovně (F3)**. Obsahuje seřazené `int[] pole`,
hledanou hodnotu `int cil` a lokální proměnnou `int vysledek = -1` v metodě `main`.
Celé binární vyhledávání píše hráč v čisté Javě. Nalezený index uloží do `vysledek`,
při nenalezení použije `-1`. Canvas automaticky sleduje čtení pole, zvýrazňuje
kontrolované indexy a animuje zmenšování intervalu. F5 spouští a pozastavuje,
F6 krokuje a posuvník mění rychlost. Definice je v
`src/main/resources/levels/0996-binary-search.properties`, režim `binarysearch`,
vstup v `array` a hledaná hodnota v `target`.

Level **Bubble sort** otevřete přes **Úrovně (F3)**. Místo skladu zobrazuje canvas
s nesetříděným polem. Výchozí kód obsahuje pouze třídu `Main`, metodu `main()`
a deklaraci vstupního `int[] pole`. Celý algoritmus včetně cyklů, porovnání a výměn
píše hráč v čisté Javě. Používá `pole.length`, `pole[i]`, operátory a běžná přiřazení
do pole. Canvas čtení a zápisy sleduje automaticky. Stejný zdroj lze přeložit
běžným `javac` i mimo hru. F5 spouští a pozastavuje, F6 krokuje,
posuvník mění rychlost. Reset obnoví stejné původní pole. Úroveň uspěje po skončení
programu se seřazeným polem, které zachová všechny původní hodnoty a jejich počty.
Konfigurace `src/main/resources/levels/09999-bubblesort.properties` používá
`mode=bubblesort` a `array=8,3,11,5,1,9,4,12,2,7,10,6` (2–32 hodnot v rozsahu 1–999).

Do editoru piš **celou Java třídu `Main` s metodou `main()`**, bez deklarace `package`.
Příkazy robota zpřístupní statický import:

```java
import static robot.api.Robot.*;

public class Main {
    public static void main(String[] args) {
        move(Direction.RIGHT);
        move(Direction.RIGHT);
        pick();
        for (int i = 0; i < 4; i++) move(Direction.LEFT);
        drop();
    }
}
```

| Příkaz | Význam |
| --- | --- |
| `move(Direction.RIGHT);` | Jeden krok ve směru `LEFT`, `RIGHT`, `TOP` nebo `DOWN` |
| `tileType()` | Vrátí `TileType.FLOOR`, `SHELF`, `DOCK` nebo `INBOUND` podle aktuální polohy |
| `robotCoordinatesX()` | Vrátí aktuální souřadnici X jako `int`, od nuly zleva |
| `robotCoordinatesY()` | Vrátí aktuální souřadnici Y jako `int`, od nuly shora |
| `shelfType()` | Sortiment aktuální police jako `ShelfType`, mimo polici `null` |
| `cargoType()` | Typ neseného balíku jako `ShelfType`, bez nákladu `null` |
| `orderRemaining(ShelfType.PARTS)` | Zbývající počet daného typu v aktuální zakázce |
| `pick();` | Vezme jeden balíček z příjmu nebo police se zásobou, pokud robot nic nenese |
| `drop();` | V nekonečném skladu uloží balík do správné police nebo jej odešle na docku; v kampani pouze expedice |

`Direction`, `TileType` a `ShelfType` jsou veřejné enumy v `robot.api.Robot`.
Stávající `import static robot.api.Robot.*;` zpřístupní metody i tyto typy.
Zboží používá `ShelfType.PARTS` (součástky), `ELECTRONICS` (elektronika), `TOOLS` (nástroje)
a `MATERIALS` (materiál). Definice úrovní používají `orange`, `purple`, `tools` a `materials`.

Dotazy si zjistí X a Y robota automaticky, nemají parametry a nemění herní stav ani
počet kroků. Čtou aktuální stav skladu a neseného nákladu.
Například `int x = robotCoordinatesX(); int y = robotCoordinatesY();` přečte
skutečnou pozici po dokončení předchozího pohybu. Souřadnice se neodvozují z animace.
Prázdný regál zůstává `TileType.SHELF` a vrací svůj sortiment; typ neurčuje dostupnou zásobu.
Nepovolené `pick()`, `drop()` nebo pohyb vyvolají chybu a zastaví hráčův program,
neúspěšná akce nezmění zásobu ani náklad.

Příklad části programu pro první úroveň:

```java
move(Direction.RIGHT);
move(Direction.RIGHT);
pick();
move(Direction.LEFT);
move(Direction.LEFT);
move(Direction.LEFT);
move(Direction.LEFT);
drop();
```

Zdroj API je v `src/main/java/robot/api/Robot.java`. Hra před překladem hráčova
programu připraví jeho třídu i enumy do samostatného pracovního adresáře.

Záložka **Konzole** zobrazuje výstup `System.out.print`, `println` a `printf`.
Zkratka `sout` v našeptávači vloží `System.out.println(...)`. Level může nastavit
`expectedOutput=PARTS` a `goal.cargo=orange`; hra pak po skončení programu zkontroluje
výpis i nesený balík. Doručovací cíle mohou být v takovém levelu nulové.
Ukázková úroveň **Co veze robot?** používá `0300b-output.properties`.

Zdroj v editoru se uloží jako `Main.java` a přeloží beze změn. Spouští se jeho
`public static void main(String[] args)`. Můžeš přidat vlastní pomocné metody a importy;
čísla řádků v chybách a při přehrávání odpovídají přesně editoru. Dříve uložené tělo
programu se při načtení automaticky vloží do třídy s metodou `main()`, jeho příkazy se zachovají.

Kód je skutečná Java: fungují proměnné, výrazy, podmínky a cykly. Příkazy nejsou
klíčová slova jazyka Java, ale metody zpřístupněné hrou. Samostatný Java proces posílá
příkazy průběžně a vždy počká na jejich dokončení ve hře. `tileType()` a `shelfType()` tak čtou aktuální stav skladu.
Program má limit 2 000 příkazů na zakázku, 3 sekundy výpočtu mezi příkazy a 64 MB haldy;
překlad má limit 12 sekund. Čekání na animaci nebo pauzu se do limitu výpočtu nepočítá.
Proces běží s oprávněními aktuálního uživatele, nejde o bezpečnostní sandbox pro cizí kód.
Při chybě překladu se program nespustí; při neplatném herním kroku se robot zastaví
a stavový řádek nad ovládacími tlačítky vypíše důvod a řádek editoru.

## Ovládání

- **F5**: překlad a nová směna / pauza / pokračování.
- **F6**: jeden příkaz nebo dotaz již načteného programu; `move()` posune robota o jedno políčko.
- **Escape**: pauza. Okno se zavírá křížkem.
- **Ctrl+R**: reset skladu, kód zůstane zachován.
- **F1**: dokumentace / návrat k Java programu.
- **F2**: přepnout sklad / editor v úzkém okně.
- **F3 / Úrovně v záhlaví**: výběr úrovně; šipky a kolečko vybírají, **Enter / Zapnout** ji aktivuje.
- **F4 / Obnovit** v nabídce úrovní: znovu načíst změněné soubory vlastních úrovní.
- **F11**: celá obrazovka na aktuálním monitoru / návrat do okna.
- **Ctrl+S**: uložit program aktuální úrovně.
- **Ctrl+A/C/X/V**, **Ctrl+Z** (zpět), **Ctrl+Y / Ctrl+Shift+Z** (znovu), Shift+šipky, Home/End a PageUp/PageDown: editor.
  Zkratky Z/Y respektují české i anglické rozložení klávesnice.
- **Ctrl+mezerník**: otevřít našeptávač; návrhy se zobrazují také po napsání dvou znaků.
- V nabídce našeptávače vybírej **šipkami**, potvrď **Enterem / Tabem** nebo kliknutím a zavři ji přes **Escape**.
- Kolečko myši posouvá editor nebo text zadání a dokumentace.
- Posuvník **Rychlost robota** pod tlačítky nastaví rychlost **1×–10×**, také během přehrávání.

Panel programu obsahuje záložky **Java program → Zadání → Dokumentace**.
Sklad a panel programu mají stejnou výšku. Vlevo od mapy jsou pod sebou panely
Zakázka a Sklad se stejnou výškou. Dohromady odpovídají výšce šachovnice.
Panel Sklad zobrazuje pouze aktuální počty balíků podle typu. V nejmenších oknech
jsou zásoby dostupné také v zadání. Přiblížení se ručně nenastavuje,
velikost rozhraní se řídí DPI monitoru.

Program se ukládá také při spuštění a zavření hry. Po úpravě kódu začne další spuštění
od výchozího stavu skladu.

## Úrovně

Úrovně se načítají z konfigurací `.properties`. Vlastnost `isVisible=true` je zobrazí
ve výběru, `isVisible=false` je skryje. Bez tohoto klíče je úroveň viditelná.
Výběr úrovní má kapitoly **Robot** pro ovládání robota a **Podklady** pro vizualizace
polí a algoritmů. Přepínají se kliknutím, šipkami vlevo/vpravo nebo Tabem;
šipky nahoru/dolů vybírají úroveň v kapitole. Seznam zobrazuje názvy úrovní.
Po otevření Podkladů se zobrazí kapitoly **Pole** a **Algoritmy**. Ze seznamu úrovní
se na ně vrátíš tlačítkem Zpět, Esc nebo Backspace.
Pole obsahuje devět úkolů: naplnění, součet a průměr, maximum, minimum,
sudá/lichá čísla, hledání, otočení, posun a druhé maximum. Následuje **Sandbox**
pro vlastní int[] libovolné délky, bez předepsaného výsledku. Velká pole se stránkují;
canvas zobrazuje právě používané pole a sleduje čtení i zápisy.
Po splnění konečné úrovně se zobrazí
tlačítko **Další level** nad skladem a místo tlačítka Spustit program; pokračovat
lze také přes **F5**. Přechod uloží řešení a otevře zadání následující úrovně
v pořadí katalogu podle `id`. Na poslední úrovni ani v nekonečném režimu se nenabízí.
Mapu, rozměry, start robota, expedici, regály, jejich zásoby, překážky a cíle určuje
UTF-8 soubor `.properties`. Vlastní definice ulož do složky `levels` v pracovním adresáři.
Jako neaktivní vzor slouží [levels/03-workshop.properties.example](levels/03-workshop.properties.example), kompletní
formát je v [levels/README.md](levels/README.md). Přibalené úrovně jsou v
`src/main/resources/levels/`. Hra automaticky najde všechny `.properties` soubory;
`index.txt` není potřeba. Názvy souborů mohou být libovolné, pořadí určuje `id`.
První platná přibalená úroveň podle `id` se použije při startu.
Při otevření nabídky F3 nebo obnovení přes F4 se v projektu načítají přímo zdrojové resources, takže
nový nebo upravený soubor nevyžaduje překlad. Zabalená hra prohledá složku `levels/`
uvnitř JAR; jeho obsah změní nové sestavení. Složka `levels` vedle hry se načítá také.

V definici úrovně můžeš nastavit také **`initialCode`** (celá Java třída),
**`assignment`** (celé zadání). Dokumentace je společná pro všechny úrovně a načítá
kapitoly z `src/main/resources/documentation/`. Starší položka `documentation`
v definicích úrovní se ignoruje.
První úroveň má počáteční kód a zadání přímo v
[`01-shift.properties`](src/main/resources/levels/01-shift.properties).
Víceřádkový zápis a nadpisy popisuje [formát úrovní](levels/README.md).
Položka `allowedKeywords` omezuje příkazy robota a řídicí konstrukce použitelné
v lekci, například `allowedKeywords=move,pick,drop,for`. Pokud chybí nebo je prázdná,
vše je dovoleno. Zakázaná konstrukce zastaví spuštění s číslem řádku a nenabízí se
v našeptávači. Importy, třída `Main`, metoda `main`, typy a proměnné zůstávají použitelné.
Omezení API rozlišuje skutečnou třídu volané metody. Vlastní metoda hráče může mít
stejný název jako zakázaný příkaz; její tělo stále musí dodržovat pravidla lekce.
Větvení podporuje `if`, `else` a `else if`. Zkratka `elseif` v našeptávači vloží
správný Java zápis `else if`; v `allowedKeywords` lze používat i položku `elseif`.
Panel objednávky načítá požadované typy a počty z `goal.*`; panel Sklad pod ním
se sestavuje z typů v `shelves` a ukazuje součet jejich aktuálních zásob.
První úroveň tak při `goal.orange=1` a jedné polici součástek zobrazí pouze součástky.
Uložené řešení má před počátečním kódem přednost; kód z definice vloží tlačítko
**Reset kódu**.

Po úpravě souboru otevři **Úrovně (F3)**, použij **Obnovit (F4)**, vyber řádek a stiskni
**Zapnout (Enter)**. Obnovení katalogu nemění současnou mapu, nová definice se použije
až při aktivaci. Neplatné soubory se vynechají a nabídka vypíše chybu. Kontrolují se
také překryvy, dostupnost expedice a dostatek dosažitelných zásilek.

Při otevření výběru se přehrávání pozastaví. Při aktivaci se sklad resetuje a načte se
program dané úrovně. Rozepsané řešení se před přepnutím automaticky uloží: první úroveň
používá původní `robot-program.java.txt`, další `programs/<id>.java.txt`. Každá úroveň
má samostatnou historii editoru. Během překladu nelze měnit aktivní mapu.

### Nekonečné zakázky

Přes **F3 → Nekonečné zakázky → Enter** zapni speciální sklad. Expedice je na **[0,1]**,
příjem (`TileType.INBOUND`) na **[1,0]**. Po `pick()` se na příjmu ihned objeví další balík.
Typy se střídají: součástky, elektronika, nástroje a materiál.

Čtyři police zůstávají na spodním řádku mapy 20×20, s jedním volným políčkem mezi nimi:

| Typ | Police | Enum |
| --- | --- | --- |
| Součástky | `[1,19]` | `ShelfType.PARTS` |
| Elektronika | `[3,19]` | `ShelfType.ELECTRONICS` |
| Nástroje | `[5,19]` | `ShelfType.TOOLS` |
| Materiál | `[7,19]` | `ShelfType.MATERIALS` |

Police začínají prázdné a mají neomezenou kapacitu. Robot unese **jeden balík**.
Po vyzvednutí na příjmu zjisti jeho typ pomocí `cargoType()` a přes `drop()` jej ulož
do police stejného typu. Chybná police balík nepřijme. Příchozí balík musí projít policí,
nelze ho odeslat přímo z příjmu na dock. Pak ho lze z police vyzvednout a odeslat.

První zakázka požaduje **1× součástky, 2× elektroniku, 1× nástroje a 1× materiál**.
Po splnění se vygeneruje nová zakázka: pro každý typ požaduje **0 až 4 kusy**, celkem
alespoň jeden balík. Dvě po sobě jdoucí zakázky se vždy liší. Police, zásoby i příjem
zůstávají zachované; zboží se automaticky nedoplňuje. Panel vlevo od mapy ukazuje
aktuální cíle i počty odevzdaných balíků, panel Sklad pod ním pouze skladové počty.
Zadání vypisuje aktuální příchozí balík, náklad robota a celkové naskladnění i expedici.

Editor obsahuje řešení s `while (true)`, které přebírá a třídí balíky a obsluhuje zakázky.
Požadavky čte průběžně přes `orderRemaining(typ)`, například
`orderRemaining(ShelfType.ELECTRONICS)`. Výsledek je počet dosud chybějících kusů;
po dokončení zakázky už další dotaz vrací požadavky nové zakázky. Dotaz nemění zásoby
ani postup. Počty v programu nezadávej napevno, protože se zakázky mění.
Přes **Reset kódu** lze vložit aktuální ukázku i při starším uloženém řešení.
F5 / Escape pozastaví běh, Ctrl+R vyprázdní sklad a vynuluje skóre. Ostatní úrovně si
zachovávají původní pravidla expedice bez příjmu a naskladňování.

Pro několik kroků používej cyklus, například `for (int i = 0; i < 3; i++) move(Direction.RIGHT);`.
Našeptávač obsahuje příkazy robota, enumy a jejich konstanty, Java klíčová slova
a šablony `for`, `if`, `while`, `switch`, `continue`, `break`, `main` / `psvm` a `sout`. Podporuje také `Robot.`
a `System.out.`. U parametrů označí výchozí hodnotu pro přepsání. Zachovává již napsané
argumenty a odsazení, nevyskakuje v komentářích a řetězcích. Jde o místní nabídku
příkazů a šablon; neprovádí sémantickou analýzu Java typů.

`continue;` přeskočí zbytek aktuální iterace cyklu, `break;` ukončí nejbližší cyklus
nebo klasický `switch`. Správný Java zápis je `break`; našeptávač opraví také zadání
`brake` na `break;`. Tyto konstrukce fungují přímo v hráčově Java programu.

```java
for (int i = 0; i < 10; i++) {
    if (i == 2) continue;
    if (i == 5) break;
    move(Direction.RIGHT);
}

int policeX = switch (cargoType()) {
    case PARTS -> 1;
    case ELECTRONICS -> 3;
    case TOOLS -> 5;
    case MATERIALS -> 7;
    case null -> -1;
};
if (policeX >= 0) {
    while (robotCoordinatesX() < policeX) move(Direction.RIGHT);
    while (robotCoordinatesX() > policeX) move(Direction.LEFT);
    while (robotCoordinatesY() < 19) move(Direction.DOWN);
}
```

Návrh `switch` vloží kostru všech větví a označí výraz `cargoType()` pro přepsání.
Větve s `->` se po vykonání automaticky ukončí. Celá šablona zachová odsazení okolního kódu.

## Monitory a čitelnost

Pozadí rozhraní se kreslí přímo ve fyzickém rozlišení framebufferu. Text vykresluje
samostatný `TextRenderer` přes OpenGL a STB TrueType, podle rendereru z předchozího
projektu: coverage atlas `GL_R8` ve dvojnásobném rozlišení, lineární filtrace a alpha
blending. Velikosti i začátky textu respektují fyzické pixely, česká diakritika je zahrnuta
v atlasu. Barevné tokeny se vykreslují pouze jednou a text respektuje ořezávání i překrytí panelů.
Velikost prvků automaticky respektuje DPI / škálování Windows a přepočítává se při
přesunu mezi monitory. Okno se při startu vejde do pracovní plochy monitoru včetně
okrajů a hlavního panelu. Při přechodu na menší monitor se jeho velikost znovu omezí.

Široké okno zobrazuje sklad a editor vedle sebe. Pod šířkou 1080 logických bodů se
zobrazí přepínatelné záložky, aby zůstalo písmo čitelné. Herní políčka zachovávají
čtvercový tvar i na širokoúhlém monitoru. Editor mění počet viditelných řádků podle
výšky okna; zadání a dokumentace se posouvají kolečkem. Velikost rozhraní má na malých oknech
automatický limit, aby se ovládací prvky vešly. Klidová obrazovka používá uloženou
texturu a znovu se kreslí při změně obsahu nebo bliknutí kurzoru.

## Font

Celé rozhraní, editor i našeptávač používají jediný přibalený **JetBrains Mono Regular**
ze souboru `src/main/resources/fonts/JetBrainsMono-Regular.ttf`. Font se načítá pouze
z resources (také uvnitř JAR). Externí soubory, systémové fonty ani nastavení fontů
přes konfigurační soubor nebo JVM parametry se nepoužívají.

## Kontroly

Společná dokumentace je v [resources/documentation](src/main/resources/documentation/README.md).
Každý `.properties` soubor je jedna kapitola. F1 otevře seznam; výběr kapitoly
ji zobrazí pouze místo panelu s mapou, objednávkou a zásobami. Vpravo je nadále
vidět editor se seznamem kapitol. Escape vrací seznam, F1 dokumentaci zavírá.

`src/test/java/org/example/GameChecks.java` je samostatný integrační test bez dalšího
testovacího frameworku. Spusť jeho `main()` v IntelliJ s plným JDK. Ověřuje překlad
Java programu, cykly, mapování řádků, chyby, limity i doručení celé první směny.

`DisplayChecks.main()` kontroluje 36 kombinací rozlišení a DPI, stejnou výšku panelů,
posuvník rychlosti, tři záložky, rozložení ovládacích prvků a mapování myši. Vytváří náhledy pro notebook, 4K a širokoúhlý
monitor v `target/display-previews/`. Změny DPI v této kontrole jsou simulované.

`CompletionChecks.main()` ověřuje doplňování, parametry, šablony, komentáře a řetězce,
ovládání klávesnicí a umístění nabídky při různých velikostech a DPI.

`TextRendererChecks.main()` ověřuje jediný přibalený JetBrains Mono, české znaky, pevné buňky editoru,
skutečné STB/OpenGL vykreslení, ořezávání, překrývání textu a změnu DPI ve skrytém okně.

`LevelChecks.main()` ověřuje definice úrovní, validaci map, zásoby jednotlivých regálů,
načítání a obnovení katalogu, aktivaci přes F3 / Enter a uchování programů při přepínání.

`EndlessChecks.main()` skutečně spustí hráčův Java proces přes 50 měnících se zakázek a 2 000 příkazů.
Ověřuje příjem, pevné police, správné naskladnění, zachování zásob mezi zakázkami,
4 100 naskladněných balíků bez kapacitního limitu, živé dotazy, pauzu, chyby a limity.
`LiveGameChecks.main()` ověřuje propojení procesu s hrou, krokování, reset a vykreslení
nekonečné úrovně ve skrytém OpenGL okně.

`RobotApiChecks.main()` ověřuje živé typované dotazy, větvení a `switch` podle enumů,
všechny směry `move`, pravidla nákladu, prázdné regály a chybové stavy.
