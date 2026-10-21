# Přibalené knihovny

Čtyři Java JAR soubory LWJGL **3.4.1** jsou součástí repozitáře, aby `spustit.cmd`
fungoval po běžném `git clone` bez Mavenu, internetu a rozbalování archivů.
Jde o nezměněné artefakty `org.lwjgl:lwjgl`, `lwjgl-glfw`, `lwjgl-opengl` a `lwjgl-stb`
z Maven Central. Nejsou zde nativní JAR ani DLL. Při aktualizaci změň společně tuto
sadu i `lwjgl.version` v `pom.xml`.

Výchozí okno používá Swing/Java2D z již nainstalovaného JDK. LWJGL Java třídy
jsou potřeba pro překlad zachovaného volitelného OpenGL rendereru; výchozí režim
nevolá jeho nativní funkce. Licence LWJGL je v [LICENSE-LWJGL.txt](LICENSE-LWJGL.txt).

Volitelný původní OpenGL režim (`-Dhrassmg.renderer=opengl`) spouštěj přes Maven
nebo IntelliJ s Maven závislostmi: potřebuje navíc nativní knihovny a jejich načítání.
