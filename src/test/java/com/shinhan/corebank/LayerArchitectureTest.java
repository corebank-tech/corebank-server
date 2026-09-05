package com.shinhan.corebank;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;
import java.util.Arrays;
import java.util.List;

/**
 * "의존 방향 adapter → application → domain" 규칙을 전 도메인에서 검증한다.
 *
 * <p>api/config는 다른 계층을 참조하지 않는 leaf 계층으로 선언한다. api는 다른 도메인에 공개하는 계약 패키지라
 * domain도 참조할 수 있고(도메인 간 공유 어휘), 그 대신 순수성을 {@code Api mayNotAccessAnyLayer}로 강제한다.
 *
 * <p>패키지 패턴은 절대 경로로 쓴다 — "..account.."는 signup.adapter.out.account 같은 다른 도메인 하위 패키지까지 잡는다.
 *
 * <p>도메인이 늘면 {@code layerRule} 호출 한 줄만 추가한다.
 */
@AnalyzeClasses(packages = "com.shinhan.corebank", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule account = layerRule("account");

    @ArchTest
    static final ArchRule auth = layerRule("auth");

    @ArchTest
    static final ArchRule autotransfer = layerRule("autotransfer");

    @ArchTest
    static final ArchRule batch = layerRule("batch", "Domain");

    @ArchTest
    static final ArchRule customer = layerRule("customer");

    @ArchTest
    static final ArchRule limit = layerRule("limit");

    @ArchTest
    static final ArchRule otp = layerRule("otp");

    @ArchTest
    static final ArchRule product = layerRule("product");

    @ArchTest
    static final ArchRule scheduledtransfer = layerRule("scheduledtransfer");

    @ArchTest
    static final ArchRule signup = layerRule("signup");

    @ArchTest
    static final ArchRule subscription = layerRule("subscription");

    @ArchTest
    static final ArchRule terms = layerRule("terms", "Application", "Domain");

    @ArchTest
    static final ArchRule transfer = layerRule("transfer");

    /**
     * 비어 있어도 되는 계층은 optionalLayers로 명시한다. 기본을 withOptionalLayers(false)로 두는 이유는, 전 계층을 optional로
     * 열면 도메인 이름에 오타가 나도 매칭 0개로 조용히 통과하기 때문이다.
     */
    private static ArchRule layerRule(String domain, String... optionalLayers) {
        String base = "com.shinhan.corebank." + domain;
        List<String> optional = Arrays.asList(optionalLayers);

        LayeredArchitecture arch = layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage(base + "..")
                .withOptionalLayers(false);
        arch = arch.optionalLayer("Api").definedBy(base + ".api..");
        arch = arch.optionalLayer("Config").definedBy(base + ".config..");
        arch = define(arch, "Domain", base + ".domain..", optional);
        arch = define(arch, "Application", base + ".application..", optional);
        arch = define(arch, "Adapter", base + ".adapter..", optional);

        return arch.whereLayer("Api")
                .mayNotAccessAnyLayer()
                .whereLayer("Config")
                .mayNotAccessAnyLayer()
                .whereLayer("Domain")
                .mayOnlyAccessLayers("Api")
                .whereLayer("Application")
                .mayOnlyAccessLayers("Domain", "Api", "Config")
                .whereLayer("Application")
                .mayOnlyBeAccessedByLayers("Adapter")
                .whereLayer("Adapter")
                .mayOnlyAccessLayers("Application", "Domain", "Api", "Config")
                .as("%s 도메인은 adapter → application → domain 의존 방향을 지킨다".formatted(domain));
    }

    private static LayeredArchitecture define(
            LayeredArchitecture arch, String name, String pkg, List<String> optional) {
        return optional.contains(name)
                ? arch.optionalLayer(name).definedBy(pkg)
                : arch.layer(name).definedBy(pkg);
    }
}
