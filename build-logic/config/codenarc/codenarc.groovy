ruleset {

    description 'A Codenarc ruleset for the Grails codebase'

    BracesForClass
    ClassStartsWithBlankLine {
        ignoreInnerClasses = true
    }
    ClosureStatementOnOpeningLineOfMultipleLineClosure
    ConsecutiveBlankLines
    FileEndsWithoutNewline
    NoTabCharacter
    DuplicateImport
    ImportFromSamePackage
    Indentation
    MisorderedStaticImports {
        comesBefore = false // static imports should come last
    }
    MissingBlankLineAfterImports
    MissingBlankLineAfterPackage
    MissingBlankLineBeforeAnnotatedField
    NoWildcardImports
    SpaceAfterCatch
    SpaceAfterClosingBrace
    SpaceAfterComma
    SpaceAfterFor
    SpaceAfterIf
    SpaceAfterMethodCallName
    SpaceAfterMethodDeclarationName
    SpaceAfterNotOperator
    SpaceAfterOpeningBrace {
        ignoreEmptyBlock = true
    }
    SpaceAfterSemicolon
    SpaceAfterSwitch
    SpaceAfterWhile
    SpaceAroundClosureArrow
    SpaceAroundMapEntryColon {
        characterAfterColonRegex = ' '
    }
    SpaceAroundOperator {
        ignoreParameterDefaultValueAssignments = false
    }
    SpaceBeforeClosingBrace {
        ignoreEmptyBlock = true
    }
    SpaceBeforeOpeningBrace
    SpaceInsideParentheses
    UnnecessaryConstructor
    UnnecessaryDotClass
    UnnecessaryGroovyImport
    UnnecessaryGString
    UnnecessaryOverridingMethod
    UnnecessaryPublicModifier
    UnnecessarySafeNavigationOperator
    UnnecessarySemicolon
    UnusedImport
}
