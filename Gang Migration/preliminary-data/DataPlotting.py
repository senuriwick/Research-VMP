import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
from scipy.interpolate import make_interp_spline

# Load CSV once
df = pd.read_csv("migration_results.csv")

# Create combined label
df['Test Case'] = df['Hosts'].astype(str) + 'x' + df['VMs'].astype(str)
df = df.sort_values(by=['Hosts', 'VMs'])
df['Test Case'] = pd.Categorical(df['Test Case'], categories=df['Test Case'].unique(), ordered=True)

# Define metrics and style
metrics = ['Total Elapsed Time (ms)', 'Average Elapsed Time (ms)']
sns.set(style="whitegrid")

# Custom colors
custom_colors = {
    'default': '#1f77b4',
    'brute': '#1a9988'
}

palette = sns.color_palette("tab10", n_colors=df['Policy'].nunique())

def line_connect():
    plt.rcParams.update({'font.size': 12})
    
    for metric in metrics:
        plt.figure(figsize=(14, 6))
        chart_data = df.groupby(['Test Case', 'Policy'])[metric].mean().reset_index()

        use_log_scale = chart_data[metric].max() / chart_data[metric].min() > 100
        if use_log_scale:
            plt.yscale('log')
            plt.ylabel(metric + " (log scale)", fontsize=14)
        else:
            plt.ylabel(metric, fontsize=14)

        sns.lineplot(
            data=chart_data,
            x='Test Case',
            y=metric,
            hue='Policy',
            marker='o',
            palette=custom_colors
        )

        plt.title(f"{metric} for Brute-force VM Placement", fontsize=16, pad=20)
        plt.xlabel("Hosts x VMs per Group", fontsize=14)
        plt.xticks(rotation=45)

        legend = plt.legend(title="Policy", title_fontsize='13', fontsize='12')
        if legend:
            plt.setp(legend.get_title(), fontsize='13')
            
        plt.tight_layout()
        plt.grid(True, linestyle='--', alpha=0.7)

        plt.tick_params(axis='both', which='major', labelsize=12)
        
        plt.savefig(f"{metric.replace(' ', '_').replace('/', '')}_lineplot.png", dpi=300)
        plt.show()

def generate_summary_table_image(csv_file="migration_results.csv", output_file="migration_summary_table.png"):
    df = pd.read_csv(csv_file)

    table_df = df[['Hosts', 'VMs', 'Total Migrations', 'Total Elapsed Time (ms)', 'Average Elapsed Time (ms)']]
    table_df = table_df.rename(columns={
        'Hosts': 'Host Count',
        'VMs': 'VMs per Group',
        'Total Migrations': 'Total Migrations',
        'Total Elapsed Time (ms)': 'Total Elapsed Time (ms)',
        'Average Elapsed Time (ms)': 'Avg Elapsed Time (ms)'
    })

    table_df['Host Count'] = table_df['Host Count'].astype(int)
    table_df['VMs per Group'] = table_df['VMs per Group'].astype(int)
    table_df['Total Migrations'] = table_df['Total Migrations'].astype(int)

    # Format all values as strings
    formatted_df = table_df.copy()
    formatted_df['Host Count'] = formatted_df['Host Count'].apply(lambda x: f"{x:,}")
    formatted_df['VMs per Group'] = formatted_df['VMs per Group'].apply(lambda x: f"{x:,}")
    formatted_df['Total Migrations'] = formatted_df['Total Migrations'].apply(lambda x: f"{x:,}")
    formatted_df['Total Elapsed Time (ms)'] = formatted_df['Total Elapsed Time (ms)'].apply(lambda x: f"{x:,.0f}" if pd.notnull(x) else "")
    formatted_df['Avg Elapsed Time (ms)'] = formatted_df['Avg Elapsed Time (ms)'].apply(lambda x: f"{x:,.2f}" if pd.notnull(x) else "")

    fig, ax = plt.subplots(figsize=(10, len(table_df) * 0.4 + 1))
    ax.axis('off')

    table = plt.table(
        cellText=formatted_df.values,
        colLabels=formatted_df.columns,
        cellLoc='left',
        loc='center',
        colColours=['#1a9988'] * len(formatted_df.columns)
    )

    col_widths = [0.8, 0.8, 0.8, 1.2, 1.2]
    for i, width in enumerate(col_widths):
        table.auto_set_column_width(i)
        table._cells[(0, i)].set_width(width)  # Header cells
        for j in range(1, len(formatted_df)+1):
            table._cells[(j, i)].set_width(width)  # Data cells

    table.auto_set_font_size(False)
    table.set_fontsize(10)
    table.scale(1.2, 1.2)

    for (i, j), cell in table.get_celld().items():
        if i == 0:  # Header row
            cell.set_text_props(color='white', weight='bold')
            cell.set_height(0.1)
        else:  # Data cells
            if j in [0, 1, 2]:  # Integer columns
                cell.set_text_props(ha='left')
            else:  # Other columns
                cell.set_text_props(ha='left')

    # Save as image
    plt.savefig(output_file, bbox_inches='tight', dpi=300, facecolor='white')
    plt.close()
    print(f"Table image saved as: {output_file}")
        
        
def main():
    line_connect()
    
    # print("Generating summary table image...")
    # generate_summary_table_image()

if __name__ == "__main__":
    main()