JAR    := ../target/gallang.jar
GALASM := galasm

.PHONY: all menu burn clean

all: menu

$(JAR):
	cd .. && mvn package -q

# ── Interactive menu (lists all .gal files dynamically) ───────────────────────
menu: $(JAR)
	@printf "\n  GALLANG Example Builder\n  ========================\n"; \
	i=1; for f in *.gal; do \
	    printf "  %d) %s\n" $$i "$${f%.gal}"; \
	    i=$$((i+1)); \
	done; \
	printf "\n  Choice [1-$$((i-1))]: "; read n; \
	i=1; for f in *.gal; do \
	    if [ "$$i" = "$$n" ]; then \
	        base="$${f%.gal}"; \
	        printf "\n  [galasm]  $$base.pld → $$base.jed.ref\n"; \
	        $(GALASM) "$$base.pld" && mv "$$base.jed" "$$base.jed.ref"; \
	        printf "\n  [gallang] $$base.gal → $$base.jed\n"; \
	        java -jar $(JAR) --svg "$$f"; \
	        exit 0; \
	    fi; \
	    i=$$((i+1)); \
	done; \
	printf "  Invalid choice: $$n\n" >&2; exit 1

# ── Burn a .jed to GAL22V10 via minipro ──────────────────────────────────────
burn:
	@jeds=$$(ls *.jed 2>/dev/null); \
	if [ -z "$$jeds" ]; then printf "  No .jed files found. Run 'make menu' first.\n" >&2; exit 1; fi; \
	printf "\n  Select .jed to burn\n  ====================\n"; \
	i=1; for f in *.jed; do \
	    printf "  %d) %s\n" $$i "$$f"; \
	    i=$$((i+1)); \
	done; \
	printf "\n  Choice [1-$$((i-1))]: "; read n; \
	i=1; for f in *.jed; do \
	    if [ "$$i" = "$$n" ]; then \
	        printf "\n  Burning $$f → GAL22V10 ...\n"; \
	        minipro -p atf22v10c -w "$$f"; exit 0; \
	    fi; \
	    i=$$((i+1)); \
	done; \
	printf "  Invalid choice: $$n\n" >&2; exit 1

# ── Clean generated files ─────────────────────────────────────────────────────
clean:
	rm -f *.jed *.jed.ref *.fus *.chp *.pin
