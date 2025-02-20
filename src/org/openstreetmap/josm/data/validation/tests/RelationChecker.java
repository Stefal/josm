// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.validation.tests;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.ChangeMembersCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetItem;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetListener;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetType;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresets;
import org.openstreetmap.josm.gui.tagging.presets.items.KeyedItem;
import org.openstreetmap.josm.gui.tagging.presets.items.Roles;
import org.openstreetmap.josm.gui.tagging.presets.items.Roles.Role;
import org.openstreetmap.josm.tools.SubclassFilteredCollection;
import org.openstreetmap.josm.tools.Utils;

/**
 * Check for wrong relations.
 * @since 3669
 */
public class RelationChecker extends Test implements TaggingPresetListener {

    // CHECKSTYLE.OFF: SingleSpaceSeparator
    /** Role ''{0}'' is not among expected values ''{1}'' */
    public static final int ROLE_UNKNOWN     = 1701;
    /** Empty role found when expecting one of ''{0}'' */
    public static final int ROLE_EMPTY       = 1702;
    /** Number of ''{0}'' roles too high ({1}) */
    public static final int HIGH_COUNT       = 1704;
    /** Number of ''{0}'' roles too low ({1}) */
    public static final int LOW_COUNT        = 1705;
    /** Role ''{0}'' missing */
    public static final int ROLE_MISSING     = 1706;
    /** Relation type is unknown */
    public static final int RELATION_UNKNOWN = 1707;
    /** Role of relation member does not match template expression ''{0}'' in preset {1} */
    public static final int WRONG_ROLE       = 1708;
    /** Type ''{0}'' of relation member with role ''{1}'' does not match accepted types ''{2}'' in preset {3} */
    public static final int WRONG_TYPE       = 1709;
    /** Relations build circular dependencies */
    public static final int RELATION_LOOP    = 1710;
    /** Relation is empty */
    public static final int RELATION_EMPTY   = 1711; // was 1708 up to r18505
    // CHECKSTYLE.ON: SingleSpaceSeparator

    // see 19312 comment:17
    private static final BooleanProperty ALLOW_COMPLEX_LOOP = new BooleanProperty("validator.relation.allow.complex.dependency", false);

    /**
     * Error message used to group errors related to role problems.
     * @since 6731
     */
    public static final String ROLE_VERIF_PROBLEM_MSG = tr("Role verification problem");
    private boolean ignoreMultiPolygons;
    private boolean ignoreTurnRestrictions;
    private final List<List<Relation>> loops = new ArrayList<>();
    /**
     * Constructor
     */
    public RelationChecker() {
        super(tr("Relation checker"),
                tr("Checks for errors in relations."));
    }

    @Override
    public void initialize() {
        TaggingPresets.addListener(this);
        initializePresets();
    }

    private static final Collection<TaggingPreset> relationpresets = new LinkedList<>();

    /**
     * Reads the presets data.
     */
    public static synchronized void initializePresets() {
        if (!relationpresets.isEmpty()) {
            // the presets have already been initialized
            return;
        }
        for (TaggingPreset p : TaggingPresets.getTaggingPresets()) {
            if (p.data.stream().anyMatch(Roles.class::isInstance)) {
                relationpresets.add(p);
            }
        }
    }

    private static final class RoleInfo {
        private int total;
    }

    @Override
    public void startTest(ProgressMonitor progressMonitor) {
        super.startTest(progressMonitor);

        for (Test t : OsmValidator.getEnabledTests(false)) {
            if (t instanceof MultipolygonTest) {
                ignoreMultiPolygons = true;
            }
            if (t instanceof TurnrestrictionTest) {
                ignoreTurnRestrictions = true;
            }
        }
    }

    @Override
    public void visit(Relation n) {
        Map<String, RoleInfo> map = buildRoleInfoMap(n);
        if (map.isEmpty()) {
            errors.add(TestError.builder(this, Severity.ERROR, RELATION_EMPTY)
                    .message(tr("Relation is empty"))
                    .primitives(n)
                    .build());
        }
        if (ignoreMultiPolygons && n.isMultipolygon()) {
            // see #17010: don't report same problem twice
            return;
        }
        if (ignoreTurnRestrictions && n.hasTag("type", "restriction")) {
            // see #17561: don't report same problem twice
            return;
        }
        Map<Role, String> allroles = buildAllRoles(n);
        if (allroles.isEmpty() && n.hasTag("type", "route")
                && n.hasTag("route", "train", "subway", "monorail", "tram", "bus", "trolleybus", "aerialway", "ferry")) {
            errors.add(TestError.builder(this, Severity.WARNING, RELATION_UNKNOWN)
                    .message(tr("Route scheme is unspecified. Add {0} ({1}=public_transport; {2}=legacy)", "public_transport:version", "2", "1"))
                    .primitives(n)
                    .build());
        } else if (n.hasKey("type") && allroles.isEmpty()) {
            errors.add(TestError.builder(this, Severity.OTHER, RELATION_UNKNOWN)
                    .message(tr("Relation type is unknown"))
                    .primitives(n)
                    .build());
        }

        if (!map.isEmpty() && !allroles.isEmpty()) {
            checkRoles(n, allroles, map);
        }
        checkLoop(n);
    }

    private static Map<String, RoleInfo> buildRoleInfoMap(Relation n) {
        Map<String, RoleInfo> map = new HashMap<>();
        for (RelationMember m : n.getMembers()) {
            map.computeIfAbsent(m.getRole(), k -> new RoleInfo()).total++;
        }
        return map;
    }

    // return Roles grouped by key
    private static Map<Role, String> buildAllRoles(Relation n) {
        Map<Role, String> allroles = new LinkedHashMap<>();

        for (TaggingPreset p : relationpresets) {
            final boolean matches = TaggingPresetItem.matches(Utils.filteredCollection(p.data, KeyedItem.class), n.getKeys());
            final SubclassFilteredCollection<TaggingPresetItem, Roles> roles = Utils.filteredCollection(p.data, Roles.class);
            if (matches && !roles.isEmpty()) {
                for (Role role: roles.iterator().next().roles) {
                    allroles.put(role, p.name);
                }
            }
        }
        return allroles;
    }

    private static boolean checkMemberType(Role r, RelationMember member) {
        if (r.types != null) {
            switch (member.getDisplayType()) {
            case NODE:
                return r.types.contains(TaggingPresetType.NODE);
            case CLOSEDWAY:
                return r.types.contains(TaggingPresetType.CLOSEDWAY);
            case WAY:
                return r.types.contains(TaggingPresetType.WAY);
            case MULTIPOLYGON:
                return r.types.contains(TaggingPresetType.MULTIPOLYGON);
            case RELATION:
                return r.types.contains(TaggingPresetType.RELATION);
            default: // not matching type
                return false;
            }
        } else {
            // if no types specified, then test is passed
            return true;
        }
    }

    /**
     * get all role definition for specified key and check, if some definition matches
     *
     * @param allroles containing list of possible role presets of the member
     * @param member to be verified
     * @param n relation to be verified
     * @return <code>true</code> if member passed any of definition within preset
     *
     */
    private boolean checkMemberExpressionAndType(Map<Role, String> allroles, RelationMember member, Relation n) {
        String role = member.getRole();
        String name = null;
        // Set of all accepted types in preset
        Collection<TaggingPresetType> types = EnumSet.noneOf(TaggingPresetType.class);
        TestError possibleMatchError = null;
        // iterate through all of the role definition within preset
        // and look for any matching definition
        for (Map.Entry<Role, String> e : allroles.entrySet()) {
            Role r = e.getKey();
            if (!r.isRole(role)) {
                continue;
            }
            name = e.getValue();
            types.addAll(r.types);
            if (checkMemberType(r, member)) {
                // member type accepted by role definition
                if (r.memberExpression == null) {
                    // no member expression - so all requirements met
                    return true;
                } else {
                    // verify if preset accepts such member
                    OsmPrimitive primitive = member.getMember();
                    if (!primitive.isUsable()) {
                        // if member is not usable (i.e. not present in working set)
                        // we can't verify expression - so we just skip it
                        return true;
                    } else {
                        // verify expression
                        if (r.memberExpression.match(primitive)) {
                            return true;
                        } else {
                            // possible match error
                            // we still need to iterate further, as we might have
                            // different preset, for which memberExpression will match
                            // but stash the error in case no better reason will be found later
                            possibleMatchError = TestError.builder(this, Severity.WARNING, WRONG_ROLE)
                                    .message(ROLE_VERIF_PROBLEM_MSG,
                                            marktr("Role of relation member does not match template expression ''{0}'' in preset {1}"),
                                            r.memberExpression, name)
                                    .primitives(member.getMember().isUsable() ? member.getMember() : n)
                                    .build();
                        }
                    }
                }
            } else if (OsmPrimitiveType.RELATION == member.getType() && !member.getMember().isUsable()
                    && r.types.contains(TaggingPresetType.MULTIPOLYGON)) {
                // if relation is incomplete we cannot verify if it's a multipolygon - so we just skip it
                return true;
            }
        }

        if (name == null) {
           return true;
        } else if (possibleMatchError != null) {
            // if any error found, then assume that member type was correct
            // and complain about not matching the memberExpression
            // (the only failure, that we could gather)
            errors.add(possibleMatchError);
        } else {
            // no errors found till now. So member at least failed at matching the type
            // it could also fail at memberExpression, but we can't guess at which

            // Do not raise an error for incomplete ways for which we expect them to be closed, as we cannot know
            boolean ignored = member.getMember().isIncomplete() && OsmPrimitiveType.WAY == member.getType()
                    && !types.contains(TaggingPresetType.WAY) && types.contains(TaggingPresetType.CLOSEDWAY);
            if (!ignored) {
                // convert in localization friendly way to string of accepted types
                String typesStr = types.stream().map(x -> tr(x.getName())).collect(Collectors.joining("/"));

                errors.add(TestError.builder(this, Severity.WARNING, WRONG_TYPE)
                        .message(ROLE_VERIF_PROBLEM_MSG,
                            marktr("Type ''{0}'' of relation member with role ''{1}'' does not match accepted types ''{2}'' in preset {3}"),
                            member.getType(), member.getRole(), typesStr, name)
                        .primitives(member.getMember().isUsable() ? member.getMember() : n)
                        .build());
            }
        }
        return false;
    }

    /**
     *
     * @param n relation to validate
     * @param allroles contains presets for specified relation
     * @param map contains statistics of occurrences of specified role in relation
     */
    private void checkRoles(Relation n, Map<Role, String> allroles, Map<String, RoleInfo> map) {
        // go through all members of relation
        for (RelationMember member: n.getMembers()) {
            // error reporting done inside
            checkMemberExpressionAndType(allroles, member, n);
        }

        // verify role counts based on whole role sets
        for (Role r: allroles.keySet()) {
            String keyname = r.key;
            if (keyname.isEmpty()) {
                keyname = tr("<empty>");
            }
            checkRoleCounts(n, r, keyname, map.get(r.key));
        }
        if ("network".equals(n.get("type")) && !"bicycle".equals(n.get("route"))) {
            return;
        }
        // verify unwanted members
        for (String key : map.keySet()) {
            if (allroles.keySet().stream().noneMatch(role -> role.isRole(key))) {
                String templates = allroles.keySet().stream()
                        .map(r -> r.key)
                        .map(r -> Utils.isEmpty(r) ? tr("<empty>") : r)
                        .distinct()
                        .collect(Collectors.joining("/"));
                List<OsmPrimitive> primitives = new ArrayList<>(n.findRelationMembers(key));
                primitives.add(0, n);

                if (!key.isEmpty()) {
                    errors.add(TestError.builder(this, Severity.WARNING, ROLE_UNKNOWN)
                            .message(ROLE_VERIF_PROBLEM_MSG, marktr("Role ''{0}'' is not among expected values ''{1}''"), key, templates)
                            .primitives(primitives)
                            .build());
                } else {
                    errors.add(TestError.builder(this, Severity.WARNING, ROLE_EMPTY)
                            .message(ROLE_VERIF_PROBLEM_MSG, marktr("Empty role found when expecting one of ''{0}''"), templates)
                            .primitives(primitives)
                            .build());
                }
            }
        }
    }

    private void checkRoleCounts(Relation n, Role r, String keyname, RoleInfo ri) {
        long count = (ri == null) ? 0 : ri.total;
        long vc = r.getValidCount(count);
        if (count != vc) {
            if (count == 0) {
                errors.add(TestError.builder(this, Severity.WARNING, ROLE_MISSING)
                        .message(ROLE_VERIF_PROBLEM_MSG, marktr("Role ''{0}'' missing"), keyname)
                        .primitives(n)
                        .build());
            } else if (vc > count) {
                errors.add(TestError.builder(this, Severity.WARNING, LOW_COUNT)
                        .message(ROLE_VERIF_PROBLEM_MSG, marktr("Number of ''{0}'' roles too low ({1})"), keyname, count)
                        .primitives(n)
                        .build());
            } else {
                errors.add(TestError.builder(this, Severity.WARNING, HIGH_COUNT)
                        .message(ROLE_VERIF_PROBLEM_MSG, marktr("Number of ''{0}'' roles too high ({1})"), keyname, count)
                        .primitives(n)
                        .build());
            }
        }
    }

    @Override
    public Command fixError(TestError testError) {
        Collection<? extends OsmPrimitive> primitives = testError.getPrimitives();
        if (isFixable(testError) && !primitives.iterator().next().isDeleted()) {
            if (testError.getCode() == RELATION_EMPTY) {
                return new DeleteCommand(primitives);
            }
            if (testError.getCode() == RELATION_LOOP) {
                Relation old = (Relation) primitives.iterator().next();
                List<RelationMember> remaining = new ArrayList<>(old.getMembers());
                remaining.removeIf(rm -> primitives.contains(rm.getMember()));
                return new ChangeMembersCommand(old, Utils.toUnmodifiableList(remaining));
            }
        }
        return null;
    }

    @Override
    public boolean isFixable(TestError testError) {
        Collection<? extends OsmPrimitive> primitives = testError.getPrimitives();
        return (testError.getCode() == RELATION_EMPTY && !primitives.isEmpty() && primitives.iterator().next().isNew())
                || (testError.getCode() == RELATION_LOOP && primitives.size() == 1);
    }

    @Override
    public void taggingPresetsModified() {
        relationpresets.clear();
        initializePresets();
    }

    @Override
    public void endTest() {
        if (Boolean.TRUE.equals(ALLOW_COMPLEX_LOOP.get())) {
            loops.removeIf(loop -> loop.size() > 2);
        }
        loops.forEach(loop -> errors.add(TestError.builder(this, Severity.ERROR, RELATION_LOOP)
                .message(loop.size() == 2 ? tr("Relation contains itself as a member")
                        : tr("Relations generate circular dependency of parent/child elements"))
                .primitives(new LinkedHashSet<>(loop))
                .build()));
        loops.clear();
        super.endTest();
    }

    /**
     * Check if a given relation is part of a circular dependency loop.
     * @param r the relation
     */
    private void checkLoop(Relation r) {
        checkLoop(r, new LinkedList<>());
    }

    private void checkLoop(Relation parent, List<Relation> path) {
        Set<Relation> pathSet = new HashSet<>(path);
        if (pathSet.contains(parent)) {
            Iterator<List<Relation>> iter = loops.iterator();
            Set<Relation> loop = new HashSet<>();
            while (iter.hasNext()) {
                loop.addAll(iter.next());
                if (loop.size() > path.size() && loop.containsAll(path)) {
                    // remove same loop with irrelevant parent
                    iter.remove();
                } else if (path.size() >= loop.size() && pathSet.containsAll(loop)) {
                    // same or smaller loop is already known
                    return;
                }
                loop.clear();
            }
            if (path.get(0).equals(parent)) {
                path.add(parent);
                loops.add(path);
            }
            return;
        }
        path.add(parent);
        for (Relation sub : parent.getMemberPrimitives(Relation.class)) {
            if (sub.isUsable() && !sub.isIncomplete()) {
                checkLoop(sub, new LinkedList<>(path));
            }
        }
    }

    /**
     * Check if adding one relation to another would produce a circular dependency.
     * @param parent the relation which would be changed
     * @param child the child relation which should be added to parent
     * @return An unmodifiable list of relations which is empty when no circular dependency was found,
     * else it contains the relations that form circular dependencies.
     * The list then contains at least two items. Normally first and last item are both {@code parent},
     * but if child is already part of a circular dependency the returned list may not end with {@code parent}.
     */
    public static List<Relation> checkAddMember(Relation parent, Relation child) {
        if (parent == null || child == null)
            return Collections.emptyList();
        RelationChecker test = new RelationChecker();
        LinkedList<Relation> path = new LinkedList<>();
        path.add(parent);
        test.checkLoop(child, path);
        if (Boolean.TRUE.equals(ALLOW_COMPLEX_LOOP.get())) {
            test.loops.removeIf(loop -> loop.size() > 2);
        }
        if (test.loops.isEmpty())
            return Collections.emptyList();
        else
            return Collections.unmodifiableList(test.loops.iterator().next());
    }

}
