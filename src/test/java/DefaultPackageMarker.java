/**
 * Exists only so {@code DataSetResourcePathResolverTest} can exercise
 * {@code DataSetResourcePathResolver}'s default-package branch, which needs a real class with
 * no package - {@code Class.forName(String)} sidesteps the default package's cross-package
 * visibility restriction that would otherwise block importing it directly from a named-package
 * test class.
 */
class DefaultPackageMarker
{
}
