JAR := ../target/gallang.jar

.PHONY: all menu andtest dfftest example clean

# Default target: show interactive menu
all: menu

# Build the fat jar if it doesn't exist yet
$(JAR):
	cd .. && mvn package -q

# ── Interactive menu ──────────────────────────────────────────────────────────
menu: $(JAR)
	@printf "\n"
	@printf "  GALLANG Example Builder\n"
	@printf "  ========================\n"
	@printf "  1) andtest  -  AND / OR gates   (Y = A·B, Z = A+B)\n"
	@printf "  2) dfftest  -  D flip-flop       (Q.r = D)\n"
	@printf "  3) example  -  Intermediate + combinatorial  (out = a + b·c)\n"
	@printf "\n"
	@printf "  Choice [1-3]: "; read n; \
	case $$n in \
	  1) $(MAKE) --no-print-directory andtest ;; \
	  2) $(MAKE) --no-print-directory dfftest ;; \
	  3) $(MAKE) --no-print-directory example ;; \
	  *) printf "  Invalid choice: $$n\n" >&2; exit 1 ;; \
	esac

# ── Individual targets ────────────────────────────────────────────────────────
andtest: $(JAR)
	java -jar $(JAR) andtest.gal
	@printf "  → andtest.jed\n"

dfftest: $(JAR)
	java -jar $(JAR) dfftest.gal
	@printf "  → dfftest.jed\n"

example: $(JAR)
	java -jar $(JAR) example.gal
	@printf "  → example.jed\n"

# ── Clean generated .jed files ────────────────────────────────────────────────
clean:
	rm -f andtest.jed dfftest.jed example.jed
