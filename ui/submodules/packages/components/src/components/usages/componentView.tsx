import { Grid } from "@mui/material";
import React from "react";
import { useParams } from "react-router-dom";
import { UsagesTable } from "./usagesTable";
import { useComponentUsagesQuery } from "../useComponentsQuery";
import { FiltersContextProvider } from "../../common";
import { Filters } from "./filters";
import { Breadcrumbs } from "./breadcrumbs";
import { UsagesFiltersModel } from "./usagesFiltersModel";

export function ComponentView(): JSX.Element {
    const { componentId } = useParams<"componentId">();
    const { data = [], isLoading } = useComponentUsagesQuery(componentId);

    return (
        <FiltersContextProvider<UsagesFiltersModel>>
            <Grid container direction="row" justifyContent="space-between" alignItems="flex-end">
                <Grid item>
                    <Breadcrumbs />
                </Grid>
                <Grid item xs={12} sm={4}>
                    <Filters />
                </Grid>
            </Grid>
            <UsagesTable data={data} isLoading={isLoading} />
        </FiltersContextProvider>
    );
}
