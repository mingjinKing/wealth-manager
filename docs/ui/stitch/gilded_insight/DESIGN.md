# Design System Specification: The Luminous Reflection

## 1. Overview & Creative North Star

### Creative North Star: "The Luminous Reflection"
Most financial apps feel like spreadsheets—cold, rigid, and anxiety-inducing. This design system rejects the "ledger" aesthetic in favor of a **High-End Editorial** experience. We are not just tracking pennies; we are curating a lifestyle of achievement. 

The "Luminous Reflection" concept treats the UI as a series of deep, obsidian-like layers. We break the "template" look through **intentional asymmetry**, where hero elements bleed off the grid, and **high-contrast typography scales** that treat financial data as a headline, not just a number. By utilizing tonal depth and light-emitting accents (Gold and Cool Blue), we shift the user's psychology from "spending guilt" to "accumulation pride."

---

## 2. Colors: Tonal Depth over Structural Lines

The palette is rooted in a deep, cosmic navy. The goal is to make the interface feel like it has physical volume.

### The "No-Line" Rule
**Explicit Instruction:** Designers are prohibited from using 1px solid borders to section off content. Traditional dividers are a relic of low-resolution design. In this system, boundaries are defined solely through background color shifts. 
- A `surface-container-low` section sitting on a `surface` background is all the separation required. 
- Use vertical white space (from the Spacing Scale) to create breathing room between logical groups.

### Surface Hierarchy & Nesting
Treat the UI as a series of stacked sheets of fine, dark glass. 
- **Deepest Layer:** `surface_container_lowest` (#0c0c1f) for the primary app backdrop.
- **Content Layer:** `surface` (#111125) for main feed areas.
- **Card/Action Layer:** `surface_container` (#1e1e32) or `surface_container_high` (#28283d) for interactive elements.

### The "Glass & Gradient" Rule
To achieve a premium, custom feel, floating elements (like Bottom Sheets or FABs) must utilize **Glassmorphism**.
- **Tokens:** Use `surface_variant` (#333348) at 60% opacity with a `20px` backdrop blur.
- **Signature Textures:** Main CTAs and Achievement Hero cards must use a linear gradient: `primary` (#ffc880) to `primary_container` (#f5a623). This provides a "glow" that flat colors cannot replicate.

---

## 3. Typography: Editorial Authority

We use a dual-font approach to balance character with readability.

- **Display & Headlines (Manrope):** This is our "Voice." Large, bold, and authoritative. Use `display-lg` (3.5rem) for total savings amounts to make them feel monumental.
- **Body & Labels (Inter):** This is our "Data." Highly legible and functional. 

**Hierarchy as Identity:**
- **The Achievement Scale:** When a user hits a saving goal, the typography should "expand." Use `headline-lg` for the amount, paired with a `label-md` in all-caps (letter-spacing: 0.05rem) to create a premium, magazine-style header.
- **The Secondary Contrast:** Always pair `on_surface` (White) for primary data with `on_surface_variant` (Amber-tinted gray) for metadata. This ensures the eye hits the most important information first.

---

## 4. Elevation & Depth: Tonal Layering

We convey hierarchy through light and shadow, not boxes.

- **The Layering Principle:** Depth is achieved by "stacking" surface-container tiers. For example, placing a `surface_container_highest` card on a `surface_container_low` section creates a natural lift.
- **Ambient Shadows:** Shadows are rare and must be "Ambient." When a floating effect is required, use a blur value of `16px-24px` with a low-opacity (6%) shadow tinted with the `surface_tint` (#ffb955) color. Never use pure black shadows; they "dirty" the dark mode.
- **The "Ghost Border" Fallback:** If accessibility requires a container edge, use a "Ghost Border": the `outline_variant` token at **15% opacity**. It should be felt, not seen.

---

## 5. Components: Precision Primitives

### Buttons
- **Primary Achievement**: Gradient (`primary` to `primary_container`). Radius: `0.5rem` (DEFAULT). These should feel like gold ingots.
- **Secondary/Safe**: `secondary_container` (#0063a9) with `on_secondary_container` (#c7deff) text.
- **Ghost/Tertiary**: No background. Use `primary` text for "Reward" actions or `secondary` for "Saving" actions.

### Cards & Lists
- **Rule**: No divider lines. Use `12` (2.75rem) or `16` (3.5rem) spacing tokens between list items.
- **Radius**: All cards must use `xl` (1.5rem/24dp) for a modern, friendly feel.
- **Interaction**: On press, a card should shift from `surface_container` to `surface_container_high`.

### The "Reflection" Input Field
- **Style**: No bottom line. A subtle `surface_container_highest` background with a `sm` (0.25rem) radius.
- **Focus**: When active, the background remains, but a 1px "Ghost Border" (20% opacity `primary`) appears to indicate focus.

### Achievement Chips
- **Style**: Fully rounded (`full`). 
- **Colors**: Use `primary_container` for positive milestones and `tertiary_container` for overspending warnings.

---

## 6. Do's and Don'ts

### Do:
- **Do** use `secondary` (Cool Blue) as the dominant "safe" color. It builds trust.
- **Do** lean into the asymmetry. Align your hero text to the left but let the "Savings Progress" graphic bleed off the right edge of the screen.
- **Do** use `display-lg` typography for monetary values. Large numbers should feel like art.

### Don't:
- **Don't** use `error` (Red) for minor alerts. Only use it for critical overspending or failed transactions.
- **Don't** use 100% opaque lines. If you think you need a line, use a spacing increment instead.
- **Don't** use standard "Material Design" shadows. Keep everything flat and tonal unless the element is physically "floating" over the interface (like a FAB).