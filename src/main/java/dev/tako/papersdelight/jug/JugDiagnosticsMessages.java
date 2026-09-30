package dev.tako.papersdelight.jug;

import dev.tako.papersdelight.config.ConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class JugDiagnosticsMessages {

    private JugDiagnosticsMessages() {
    }

    public static List<String> lines(@Nullable JugDiagnosticsReport report) {
        if (report == null) return List.of();
        List<String> lines = new ArrayList<>();
        lines.add(ConfigManager.getOr("jug_diagnostics_header", "§6Jug Diagnostics"));

        if (!report.libuidAvailable()) {

            lines.add(ConfigManager.getOr("jug_diagnostics_libuid_missing", "§7Libuid: §cunavailable"));
            lines.add(ConfigManager.getOr("jug_requires_libuid", "Libuid is unavailable; the Jug mechanic is disabled."));
            return List.copyOf(lines);
        }

        lines.add(ConfigManager.getOr("jug_diagnostics_libuid_present", "§7Libuid: §aavailable"));
        lines.add(ConfigManager.getOr(report.runtimeInstalled()
                ? "jug_diagnostics_runtime_installed"
                : "jug_diagnostics_runtime_missing", report.runtimeInstalled()
                ? "§7Runtime: §ainstalled"
                : "§7Runtime: §cnot installed"));
        lines.add(ConfigManager.getOr("jug_diagnostics_recipes",
                        "§7Recipes: §f%total% total §7(filling %filling%, emptying %emptying%, soaking %soaking%)")
                .replace("%total%", String.valueOf(report.totalRecipes()))
                .replace("%filling%", String.valueOf(report.filling()))
                .replace("%emptying%", String.valueOf(report.emptying()))
                .replace("%soaking%", String.valueOf(report.soaking())));

        if (report.fluids().isEmpty()) {
            lines.add(ConfigManager.getOr("jug_diagnostics_no_fluids", "§7Fluids: §eno fluids registered"));
            return List.copyOf(lines);
        }

        lines.add(ConfigManager.getOr("jug_diagnostics_fluids_header",
                        "§7Fluid display items §f(%count%)").replace("%count%", String.valueOf(report.fluids().size())));
        for (JugDiagnosticsReport.FluidEntry entry : report.fluids()) {
            String item = entry.displayItemId();
            lines.add(ConfigManager.getOr(item == null
                            ? "jug_diagnostics_fluid_entry_missing"
                            : "jug_diagnostics_fluid_entry",
                            item == null ? "§7- %fluid% §cmissing" : "§7- %fluid% §8→ §f%item%")
                    .replace("%fluid%", entry.fluidKey())
                    .replace("%item%", item == null ? "" : item));
        }

        if (report.missingFluids().isEmpty()) {
            lines.add(ConfigManager.getOr("jug_diagnostics_fluids_all_present", "§aAll fluids have a dedicated display item."));
        } else {
            lines.add(ConfigManager.getOr("jug_diagnostics_fluids_missing",
                            "§e%count% fluid(s) lack a dedicated display item: §f%fluids%")
                    .replace("%count%", String.valueOf(report.missingFluids().size()))
                    .replace("%fluids%", String.join(", ", report.missingFluids())));
        }
        return List.copyOf(lines);
    }
}
