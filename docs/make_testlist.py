#!/usr/bin/env python3
"""Builds the printable MouthBand test checklist (HTML -> PDF with LibreOffice)."""
import html, subprocess, sys, pathlib

OK = ("OK", "Fehler")

SECTIONS = [
 ("1  Start und Onboarding", [
  ("Kaltstart: App aus der Liste der letzten Apps wegwischen und neu öffnen. Erste Seite erscheint in unter 1,5 s.", ("schnell", "spürbar langsam")),
  ("Seite 1/3 „Make songs with your voice“ → Next.", OK),
  ("Seite 2/3: „Allow microphone“ → Android-Dialog erscheint, „While using the app“ wählen.", OK),
  ("Seite 3/3 „Quick sound check“ → „Start measuring“ (Handy auf den Tisch, Lautstärke hoch): Messung läuft, Ergebnis in ms.", OK),
  ("„Skip for now“ → Home. Settings zeigen „150 ms · not measured“.", OK),
  ("Seitenwechsel im Onboarding (Überblendung) ist weich.", ("weich", "ruckelt")),
 ]),
 ("2  Home", [
  ("Leerer Zustand: Karte „No songs yet“ sichtbar.", OK),
  ("„Start a new song“ → Vibe-Schritt öffnet.", OK),
  ("Songkarte zeigt Titel, BPM · Tonart, 8 Fortschrittsbalken und Status („Step X of 8 …“).", OK),
  ("Karte antippen → öffnet am richtigen Schritt (fertiger Song → Arrange).", OK),
  ("Menü ⋮ → Rename: Name ändern und speichern.", OK),
  ("Menü ⋮ → Duplicate: Kopie erscheint, ist unabhängig vom Original.", OK),
  ("Menü ⋮ → Delete: Bestätigung, danach ist der Song weg.", OK),
  ("Banner-Werbung nur auf Home, unten, überdeckt nichts.", OK),
  ("Karten erscheinen/verschwinden/rutschen weich (Hinzufügen, Löschen).", ("weich", "ruckelt")),
  ("Einstellungs-Knopf (oben rechts) → Settings.", OK),
  ("Zurück-Taste auf Home → App geht in den Hintergrund, bleibt am Leben, Wieder-Öffnen geht.", OK),
 ]),
 ("3  Vibe (Schritt 1)", [
  ("Mood-Karten Chill / Pop / Hype / Sad / Rock: Auswahl färbt sich, Tempo und Tonart ändern sich.", OK),
  ("Tempo: Pfeile ◀ ▶ ändern die BPM um 1.", OK),
  ("Tempo-Schieber zieht sich flüssig.", ("flüssig", "hakt")),
  ("„Tap tempo“: 4× im Takt tippen → BPM passt sich an.", OK),
  ("Tonart / Dur-Moll ändern.", OK),
  ("Loop-Länge 2 Bars / 4 Bars umschalten.", OK),
  ("Nach dem ersten gespeicherten Take: Hinweis, dass Tempo, Tonart und Länge gesperrt sind.", ("gesperrt + Hinweis", "lässt sich ändern")),
  ("„Next: beat“ → Beat-Schritt.", OK),
 ]),
 ("4  Coach-Karte (Schritte 2–6: Beat, Bass, Chords, Melody, Vocals)", [
  ("Titel, „STEP 0X / 08“ und Fortschrittsleiste (aktuelle Spur in ihrer Farbe) stimmen.", OK),
  ("„Hear an example first“ spielt das Beispiel; nochmal tippen stoppt es.", OK),
  ("Mic-Meter (20 Segmente) reagiert live auf deine Stimme.", ("reagiert", "tot / verzögert")),
  ("Meter-Urteil: still → „Too quiet“, normal → „Good“, sehr laut / Mund am Mikro → „Too loud“.", OK),
  ("Kabel-Kopfhörer anstecken → grüner Chip „Headphones connected“.", OK),
  ("Bluetooth-Kopfhörer verbinden → Warn-Sheet wegen Latenz erscheint.", OK),
  ("Mikrofon-Erlaubnis verweigern → Seite „needs the microphone“ mit Allow / App settings, kein Absturz.", OK),
  ("Mikrofon von anderer App belegt (z. B. Sprachrecorder offen) → Meldung statt Absturz.", OK),
  ("„Skip step“ → nächster Schritt.", OK),
  ("Bei vorhandenem Take: „Edit the take you kept“ ist sichtbar.", OK),
  ("Zurück-Pfeil und Zurück-Taste führen zur vorherigen Seite.", OK),
 ]),
 ("5  Teach your sounds (erster Beat-Take oder Settings → Teach sounds)", [
  ("„boom“, „pff“, „tss“ je 4× sagen: jeder Treffer wird gezählt.", OK),
  ("Reset in Settings → danach arbeitet die einfache Erkennung wieder.", OK),
  ("Nach dem Lernen geht es zur Aufnahme weiter.", OK),
 ]),
 ("6  Aufnahme (Indigo-Bildschirm)", [
  ("Record-Taste → Bildschirm wächst weich ein.", ("weich", "ruckelt / springt")),
  ("Count-in: „Count-in“, Klicks 1-2-3-4, Beat-Kästchen leuchten genau im Takt.", ("im Takt", "zu spät")),
  ("Danach „BAR 1/2“, Überschrift „Keep going“, im letzten Takt „Last bar“.", OK),
  ("Timer läuft; Aufnahme endet am Loop-Ende, dann „Processing …“.", OK),
  ("Frühere Spuren sind beim Aufnehmen hörbar; Klick lässt sich in Settings abschalten.", OK),
  ("Live-Wellenform: jeder Ton erscheint dort, wo du ihn gehört hast; Abspielkopf läuft ruhig.", ("passt", "versetzt / hinkt")),
  ("Beat: Live-Drum-Spur (Kick / Snare / Hi-Hat) zeigt jeden Schlag sofort an der richtigen Stelle.", ("sofort", "verzögert / fehlt")),
  ("Bass / Chords / Melody: Tonhöhen-Linie folgt der Stimme durchgehend, Lücken bei Pausen.", ("folgt", "springt / fehlt")),
  ("Flüstern: Wellenform ist trotzdem gut sichtbar.", ("sichtbar", "kaum zu sehen")),
  ("Schreien / laut: Wellenform bleibt in der Spur, flackert nicht.", ("ruhig", "flackert")),
  ("Gesamteindruck der Anzeige während der Aufnahme.", ("flüssig", "ruckelt")),
  ("Stop-Taste beendet früher; der Take wird trotzdem verarbeitet.", OK),
  ("„Cancel“ → zurück zur Coach-Karte, nichts gespeichert.", OK),
  ("Zurück-Taste während der Aufnahme = Abbruch.", OK),
  ("Anruf oder App-Wechsel während der Aufnahme → stoppt sauber, kein Absturz.", OK),
  ("Nichts sagen (Stille) → „didn't hear anything“, nichts wird gespeichert.", OK),
  ("Bass-Schritt nur klatschen / Rauschen → Meldung statt falscher Noten.", ("Meldung", "falsche Noten")),
 ]),
 ("7  Review (nach jeder Aufnahme)", [
  ("Review gleitet von unten ein und spielt automatisch als Loop.", OK),
  ("Play / Pause-Taste.", OK),
  ("A/B-Umschalter: „Original“ = deine Aufnahme, „Converted“ = Instrument (bei Vocals nicht vorhanden).", OK),
  ("Beat: Zelle im Drum-Gitter antippen schaltet den Schlag an / aus.", OK),
  ("„Bar 1“ / „Bar 2“ umschalten.", OK),
  ("Piano-Roll: die Noten entsprechen dem, was du gesummt hast.", ("stimmen", "falsche Töne")),
  ("Note antippen → ausgewählt (Rahmen) und Text „… selected“.", OK),
  ("„Delete note“ löscht die ausgewählte Note.", OK),
  ("„Undo“ bringt gelöschte Noten zurück (auch mehrere Schritte).", OK),
  ("Leere Zelle antippen fügt eine Note hinzu.", OK),
  ("Chords: Akkord antippen wählt ALLE Töne; „Delete chord“ löscht den ganzen Akkord.", OK),
  ("Sound-Chips: anderen Sound antippen → klingt sofort anders.", OK),
  ("Beat: Drum-Kit wechseln (Boom-Bap, Trap, Lo-fi, Rock, Pop).", OK),
  ("Timing-Schieber: „Human“ (roh) ↔ „Tight“ (aufs Raster) ist hörbar.", OK),
  ("„Try again“ → zurück zur Coach-Karte, Take verworfen.", OK),
  ("„Keep it · next“ → nächster Schritt, Take ist gespeichert.", OK),
  ("Zurück-Taste im Review verhält sich wie „Try again“.", OK),
  ("Vocals: Take klingt klar, weder übersteuert noch zu leise.", ("klar", "zu leise / verzerrt")),
 ]),
 ("8  Pegel und Klarheit (neu)", [
  ("Sehr leise summen (Handy weit weg): Noten werden trotzdem erkannt.", ("erkannt", "keine Noten")),
  ("Sehr laut summen / Mund nah am Mikro: Noten erkannt, Klang nicht verzerrt.", ("erkannt", "verzerrt / keine Noten")),
  ("Normale Lautstärke: richtige Töne.", OK),
  ("Beatbox sehr leise: Kick / Snare / Hi-Hat in den richtigen Reihen.", OK),
  ("Beatbox sehr laut: ebenfalls richtig.", OK),
  ("Vocals leise aufgenommen: im Review und in Arrange laut genug.", ("laut genug", "zu leise")),
  ("Nur Raumrauschen / Lüfter / Fernseher: „didn't hear anything“, keine Phantom-Noten.", ("Meldung", "Phantom-Noten")),
  ("Gespeicherter Take hat kein Brummen und kein Knacken.", ("sauber", "Störgeräusch")),
 ]),
 ("9  Arrange (Schritt 7)", [
  ("Ein fertiger Song (Schritt 8 erreicht) öffnet in Arrange, nicht in Export.", OK),
  ("Abschnitts-Leiste (Intro / Verse / Chorus / Verse / Chorus / Outro): Block antippen → Sheet mit Schaltern.", OK),
  ("Schalter an / aus ändert die Farbbalken im Block.", OK),
  ("Play Song: spielt, aktueller Abschnitt wird hervorgehoben, Stop beendet.", OK),
  ("Fader pro Spur: Lautstärke live hörbar, bleibt gespeichert.", OK),
  ("Mute (Lautsprecher-Knopf): Spur stumm, nochmal = wieder an.", OK),
  ("„Auto-mix my song“: Pegel und Pan ändern sich; nochmal tippen = aus.", OK),
  ("Nicht aufgenommene Spur zeigt „not recorded“ und ist ausgegraut.", OK),
  ("Leerer Song: Hinweis „Nothing recorded yet“ mit Rückweg.", OK),
  ("Alle Spuren stumm → Hinweis „Nothing to play yet“.", OK),
  ("App während des Abspielens verlassen → Wiedergabe stoppt.", OK),
  ("Zurück → Home.", OK),
 ]),
 ("10  Fertigen Song bearbeiten und mischen (Spur-Sheet)", [
  ("Spurzeile antippen („Bass ›“) → Sheet öffnet.", OK),
  ("Sound-Chip antippen: Sound wird übernommen UND die Spur spielt allein; Zeile zeigt danach den neuen Namen.", OK),
  ("Bass: Synth Bass, Deep Sub, Electric Bass klingen unterscheidbar.", ("verschieden", "gleich")),
  ("Chords: Soft Keys, Warm Pad, Organ klingen unterscheidbar.", ("verschieden", "gleich")),
  ("Melody: Piano, Flute, Bright Lead, Bell klingen unterscheidbar.", ("verschieden", "gleich")),
  ("Beat: die 5 Drum-Kits klingen unterscheidbar.", ("verschieden", "gleich")),
  ("Pan-Schieber (Kopfhörer): Spur wandert links ↔ rechts, bleibt gespeichert.", OK),
  ("„Hear this part“ / „Stop“.", OK),
  ("Stumm geschaltete Spur lässt sich trotzdem anhören und bleibt danach stumm.", OK),
  ("Stimme: Sheet ohne Sound-Chips, mit Pan, Hear und Record again.", OK),
  ("Sheet schließen beendet das Anhören.", OK),
  ("Bleistift „Edit notes“: Noten löschen / hinzufügen → „Save changes“ → zurück in Arrange, Änderung hörbar.", OK),
  ("Editor mit Zurück verlassen → Änderungen verworfen, Arrange.", OK),
  ("„Record again“: nach neuem „Keep it“ landest du wieder in Arrange (nicht im Coach).", ("Arrange", "Coach")),
  ("Coach-Karte „Edit the take you kept“ → Editor → „Save changes“ → zurück zur Coach-Karte.", OK),
 ]),
 ("11  Export (Schritt 8)", [
  ("„Export & share“ öffnet den Export-Bildschirm.", OK),
  ("Format M4A / WAV umschalten.", OK),
  ("Rendern: Fortschritt läuft, „Cancel“ bricht ab.", OK),
  ("„Save to phone“ → Ordner wählen, Datei erscheint und spielt in Dateien / WhatsApp.", OK),
  ("„Share“ → Teilen-Menü, Datei kommt in der Ziel-App an.", OK),
  ("Play-Taste im Export spielt den Song wie gerendert.", OK),
  ("Klang des Exports = Arrange (nicht übersteuert, kein Knacken, richtige Länge).", ("gleich", "anders")),
  ("Vollbild-Werbung nur nach erfolgreichem Export, nicht in der 1. Sitzung, höchstens alle ~4 Min, nie bei Wiedergabe / Aufnahme.", OK),
  ("Home-Status „Exported · …“.", OK),
  ("Zurück → Arrange.", OK),
 ]),
 ("12  Settings", [
  ("„Latency calibration“ zeigt Wert und Status.", OK),
  ("„Re-run calibration“ (Handy auf Tisch) → Ergebnis in ms.", OK),
  ("„Fine-tune by ear“ → letzter Take, Schieber verschiebt hörbar.", OK),
  ("„Teach sounds“ / Reset.", OK),
  ("„Click sound“ aus = kein Metronom beim Aufnehmen.", OK),
  ("„Count-in“ 1 bar / 2 bars.", OK),
  ("„Privacy and ad consent“: EU → Formular, sonst Hinweis „No privacy choices …“.", OK),
  ("„Licenses“ öffnet und scrollt.", OK),
  ("„Version 1.0.0“ angezeigt; Zurück funktioniert.", OK),
 ]),
 ("13  Animationen und Navigation (Hauptwunsch)", [
  ("Vorwärts: neuer Bildschirm schiebt von rechts herein, der alte driftet leicht nach links.", ("weich", "ruckelt / springt")),
  ("Zurück-Taste: Bildschirm schiebt nach rechts hinaus (Spiegel der Vorwärts-Bewegung).", ("weich", "ruckelt / springt")),
  ("Vorhersage-Zurück: langsam vom Rand wischen → Bildschirm folgt dem Finger, Loslassen vollendet oder bricht ab.", ("folgt dem Finger", "springt")),
  ("Zurück auf Home / erster Onboarding-Seite: App geht in den Hintergrund, kein Absturz.", OK),
  ("Knöpfe und Karten sinken beim Drücken leicht und federn zurück.", ("ja", "nein")),
  ("Umschalter (Bar 1/2, Count-in, M4A/WAV): Auswahl gleitet.", ("gleitet", "springt")),
  ("Chips, Mood-Karten, Fortschrittsleisten wechseln Farbe sanft.", ("sanft", "hart")),
  ("Coach → Aufnahme → Review: Übergänge (Einblenden + Wachsen / Hochschieben).", ("weich", "ruckelt")),
  ("Review: Leiste mit „Delete / Undo“ gleitet ein und aus.", ("gleitet", "springt")),
  ("Gesamteindruck beim Navigieren durch die App.", ("flüssig", "ruckelt")),
  ("Aufnahme-Bildschirm: Wellenform, Abspielkopf und Pegel laufen ruhig.", ("ruhig", "ruckelt")),
  ("Systemeinstellung „Animationen entfernen“ an: App wechselt ohne Animation, nichts hängt.", OK),
 ]),
 ("14  System und Robustheit", [
  ("Tablet / Faltgerät (falls vorhanden): Layout zentriert und lesbar, Drehen ok.", OK),
  ("App bei Wiedergabe in den Hintergrund: Ton stoppt, Rückkehr ok.", OK),
  ("Eingehender Anruf bei Aufnahme / Wiedergabe: stoppt sauber.", OK),
  ("Kopfhörer bei Wiedergabe abziehen: Wiedergabe stoppt.", OK),
  ("Größte Systemschrift: nichts abgeschnitten.", ("lesbar", "abgeschnitten")),
  ("TalkBack: Coach-Ablauf bedienbar, Buttons beschriftet, Noten haben „Delete“-Aktion.", OK),
  ("Viele Songs (5+): Liste scrollt flüssig.", ("flüssig", "ruckelt")),
  ("App beenden und neu starten: Songs, Takes und Einstellungen sind noch da.", OK),
  ("Flugmodus: App funktioniert komplett (nur Werbung fehlt).", OK),
  ("Speicher fast voll (falls testbar): Fehlermeldung statt Absturz.", OK),
  ("1–2 Minuten wild durchtippen: kein Absturz, kein „reagiert nicht“.", ("stabil", "Absturz / ANR")),
  ("10 Takes hintereinander: Treffer bleiben auf dem Raster (synchron).", ("synchron", "driftet")),
 ]),
 ("15  Datenschutz und Werbung", [
  ("Werbung nur als Home-Banner und als Vollbild nach Export; nie bei Aufnahme, Review oder Arrange.", OK),
  ("Keine Speicher-Berechtigung verlangt (Export läuft über den Systemdialog).", OK),
  ("Android-Einstellungen → App → Berechtigungen: nur Mikrofon.", OK),
  ("Langes Drücken auf den Titel „MouthBand“ auf Home tut nichts (kein Demo-Menü im Release).", OK),
 ]),
]

MEASURE = [
 "Kaltstart (Zeit bis zur ersten Seite):  ________ ms        Ziel: unter 1500 ms",
 "Ruckelnde Frames beim Navigieren:  ________ %        Ziel: höchstens 5 %",
 "Gerät / Android-Version:  ______________________________________________",
 "Gesamteindruck Flüssigkeit (1 = schlecht … 5 = sehr gut):   1    2    3    4    5",
]

CSS = """
@page { size: 21cm 29.7cm; margin: 1.6cm 1.5cm 1.6cm 1.5cm; }
body { font-family: 'DejaVu Sans', sans-serif; font-size: 9.5pt; }
h1 { font-family: 'DejaVu Sans', sans-serif; font-size: 20pt; margin: 0 0 4pt 0; }
h2 { font-family: 'DejaVu Sans', sans-serif; font-size: 11.5pt; background-color: #1a0f6e; color: #ffffff; padding: 4pt 6pt; margin: 14pt 0 4pt 0; }
p.small { font-size: 8.5pt; color: #333333; margin: 2pt 0; }
table { border-collapse: collapse; width: 100%; }
td, th { border: 0.6pt solid #888888; padding: 3pt 4pt; vertical-align: middle; }
th { background-color: #e8e4f5; font-size: 8.5pt; text-align: left; }
td.n { width: 6%; text-align: center; color: #444444; }
td.t { width: 54%; }
td.o { width: 20%; }
.box { font-size: 14pt; }
p.line { border-bottom: 0.6pt solid #666666; margin: 10pt 0 0 0; height: 14pt; }
"""

def row(sec, i, text, opts):
    a, b = opts
    return (f"<tr><td class='n' width='7%'>{sec}.{i}</td><td class='t' width='53%'>{html.escape(text)}</td>"
            f"<td class='o' width='20%'><span class='box'>☐</span> {html.escape(a)}</td>"
            f"<td class='o' width='20%'><span class='box'>☐</span> {html.escape(b)}</td></tr>")

def build():
    parts = [f"<html><head><meta charset='utf-8'><style>{CSS}</style></head><body>"]
    parts.append("<h1>MouthBand – Testliste</h1>")
    parts.append("<p class='small'>Version 1.0.0 · Release-Build auf dem Handy. In jeder Zeile genau EINE Box ankreuzen: links das erwartete Ergebnis, rechts das Problem. "
                 "Bei einem Problem kurz in die Notizzeile am Ende des Abschnitts schreiben, was passiert ist (Schritt, was du gesehen oder gehört hast).</p>")
    parts.append("<p class='small'>Tester: ____________________   Gerät / Android: ____________________   Datum: ______________</p>")
    for si, (title, items) in enumerate(SECTIONS, 1):
        parts.append(f"<h2>{html.escape(title)}</h2>")
        parts.append("<table border='1' cellspacing='0' cellpadding='5' width='100%'><tr><th width='7%'>Nr.</th><th width='53%'>Was tun → was soll passieren</th><th width='40%' colspan='2'>Ergebnis (eine Box)</th></tr>")
        for i, (text, opts) in enumerate(items, 1):
            parts.append(row(si, i, text, opts))
        parts.append("</table>")
        parts.append("<p class='small'>Notizen:</p><p class='line'>&nbsp;</p><p class='line'>&nbsp;</p>")
    parts.append("<h2>16  Messwerte (eintragen)</h2>")
    for m in MEASURE:
        parts.append(f"<p>{html.escape(m)}</p>")
    parts.append("<p class='small'>Tipp: Mit angeschlossenem Handy (USB-Debugging an) misst <b>tools/phone_check.sh</b> die ersten beiden Werte automatisch.</p>")
    parts.append("<h2>Allgemeine Notizen</h2>")
    for _ in range(8):
        parts.append("<p class='line'>&nbsp;</p>")
    parts.append("</body></html>")
    return "\n".join(parts)

if __name__ == "__main__":
    out = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "MouthBand_Testliste.html")
    out.write_text(build(), encoding="utf-8")
    total = sum(len(i) for _, i in SECTIONS)
    print(f"{out} written, {len(SECTIONS)} sections, {total} checks")
